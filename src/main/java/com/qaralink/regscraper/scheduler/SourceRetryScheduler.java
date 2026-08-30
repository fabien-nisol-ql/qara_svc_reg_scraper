package com.qaralink.regscraper.scheduler;

import com.qaralink.regscraper.model.db.ScrapeJobEntity;
import com.qaralink.regscraper.model.db.SourceEstimateEntity;
import com.qaralink.regscraper.model.db.SourceRetryStateEntity;
import com.qaralink.regscraper.model.db.repo.SourceEstimateRepository;
import com.qaralink.regscraper.model.db.repo.ScrapeJobRepository;
import com.qaralink.regscraper.model.dto.JobStatus;
import com.qaralink.regscraper.model.dto.RegulationSourceDTO;
import com.qaralink.regscraper.svc.RegulationSourceService;
import com.qaralink.regscraper.svc.ScrapeJobService;
import com.qaralink.regscraper.svc.SourceRetryStateService;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Automatically re-triggers every known source on an ongoing basis — the
 * single scheduler behind a source's own "next automatic try" — using one
 * of two cadences depending on whether it's caught up:
 * <ul>
 *     <li>Still incomplete (backlog remaining, or the latest job failed):
 *     roughly once per {@code qaralink.scheduler.retry-interval-minutes}
 *     (default 60) — until it either catches up or hits
 *     {@code qaralink.scheduler.retry-max-consecutive-failures} (default
 *     5) consecutive failed attempts, at which point it's suspended
 *     (flagged for engineering review, GET /v1/retry-state) instead of
 *     retried forever.
 *     <li>Fully caught up and the last run succeeded: roughly once per
 *     {@code qaralink.scheduler.steady-state-check-interval-minutes}
 *     (default 1440, i.e. once a day) — just often enough to notice a
 *     newly published document without hammering an already-current
 *     source hourly. The same circuit breaker still applies: a
 *     steady-state check that starts failing counts toward the same
 *     consecutive-failure threshold and can still suspend.
 * </ul>
 * A third factor overrides both cadences above, whenever it applies:
 * {@link SourceEstimateEntity#getNextAvailableAt()} — set by
 * qara_cli_reg_scraper when a source's own host declares a robots.txt
 * {@code Visiting-hours} window (confirmed live on
 * {@code accessdata.fda.gov} — see that repo's {@code robots_policy.py}
 * and README) and the source is currently outside it. Triggering a job
 * during a closed window would just immediately no-op (the CLI enforces
 * the same window itself), so this scheduler defers straight to that
 * reported reopen time instead — null for the vast majority of sources,
 * which are entirely unaffected.
 * <p>
 * Entirely independent of {@link ScraperAutoRunScheduler}'s own daily cron
 * (opt-in via {@code qaralink.scheduler.sources}, empty/unused by
 * default) — that one, if configured, still runs on its own fixed
 * wall-clock schedule, unrelated to this per-source state machine.
 * <p>
 * Ticks far more often ({@code qaralink.scheduler.retry-check-cadence-minutes},
 * default 1) than sources are actually re-triggered — cheap since it only
 * iterates the known-source registry (~7-20 rows today), and lets
 * {@link SourceRetryStateEntity#getNextRetryAt()} carry sub-minute
 * precision for a UI's "next automatic try" display rather than only ever
 * landing on the hour (or the day).
 * <p>
 * State machine per source, per tick:
 * <ol>
 *     <li>Suspended already? Skip entirely — needs a human (a manual
 *     trigger via {@code POST /v1/jobs/scrape} clears this, see
 *     {@link ScrapeJobService#trigger}).
 *     <li>Compute {@code needsWork}: no {@link SourceEstimateEntity} row
 *     yet, or its {@code remaining} is null (source can't cheaply report
 *     one) or &gt;0, OR the latest {@link ScrapeJobEntity} for this source
 *     failed. Absence/unknown both count as "needs work," not "caught up."
 *     <li>Not needing work? Clear any stale failure history (a source
 *     that recovered on its own — e.g. a manual run — shouldn't carry old
 *     counts) but keep going below, at the slower steady-state cadence,
 *     rather than stopping.
 *     <li>React to the latest job's terminal outcome, but only once per
 *     job (tracked via {@code lastEvaluatedJobId} — this tick runs every
 *     minute, actual re-triggers happen far less often, so the same
 *     terminal job must not be double-counted on every intervening tick).
 *     Applies whether caught up or not, so a steady-state check that
 *     fails still counts toward the threshold below.
 *     <li>Hit the failure threshold? Suspend, don't trigger.
 *     <li>Otherwise, if due ({@code nextRetryAt} null or in the past),
 *     trigger a fresh check via {@link ScrapeJobService#triggerRetry} and
 *     schedule the next one at whichever cadence currently applies.
 * </ol>
 */
@Singleton
public class SourceRetryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SourceRetryScheduler.class);

    private final RegulationSourceService sourceService;
    private final SourceRetryStateService retryStateService;
    private final SourceEstimateRepository estimateRepository;
    private final ScrapeJobRepository jobRepository;
    private final ScrapeJobService jobService;
    private final int retryIntervalMinutes;
    private final int steadyStateIntervalMinutes;
    private final int maxConsecutiveFailures;

    public SourceRetryScheduler(
            RegulationSourceService sourceService,
            SourceRetryStateService retryStateService,
            SourceEstimateRepository estimateRepository,
            ScrapeJobRepository jobRepository,
            ScrapeJobService jobService,
            @Value("${qaralink.scheduler.retry-interval-minutes}") int retryIntervalMinutes,
            @Value("${qaralink.scheduler.steady-state-check-interval-minutes}") int steadyStateIntervalMinutes,
            @Value("${qaralink.scheduler.retry-max-consecutive-failures}") int maxConsecutiveFailures
    ) {
        this.sourceService = sourceService;
        this.retryStateService = retryStateService;
        this.estimateRepository = estimateRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.retryIntervalMinutes = retryIntervalMinutes;
        this.steadyStateIntervalMinutes = steadyStateIntervalMinutes;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    @Scheduled(fixedDelay = "${qaralink.scheduler.retry-check-cadence-minutes}m")
    void tick() {
        for (RegulationSourceDTO known : sourceService.all()) {
            try {
                evaluate(known.getRegulation(), known.getSource());
            } catch (Exception e) {  // one source's bug must not stop every other source's evaluation
                LOG.error("Retry evaluation failed for {}:{}", known.getRegulation(), known.getSource(), e);
            }
        }
    }

    private void evaluate(String regulation, String source) {
        String qualifiedName = regulation + ":" + source;
        SourceRetryStateEntity state = retryStateService.findOrNew(regulation, source);

        if (Boolean.TRUE.equals(state.getSuspended())) {
            return;
        }

        Optional<ScrapeJobEntity> latestJob = jobRepository
                .search(qualifiedName, Pageable.from(0, 1, Sort.of(Sort.Order.desc("submittedAt"))))
                .getContent().stream().findFirst();
        Optional<SourceEstimateEntity> estimate = estimateRepository.findByRegulationAndSource(regulation, source);

        boolean lastJobFailed = latestJob.map(j -> j.getStatus() == JobStatus.FAILED).orElse(false);
        boolean needsWork = estimate.isEmpty()
                || estimate.get().getRemaining() == null
                || estimate.get().getRemaining() > 0
                || lastJobFailed;

        if (!needsWork && (state.getConsecutiveFailures() != 0 || Boolean.TRUE.equals(state.getSuspended()))) {
            // Caught up and healthy again (e.g. after a manual run) — clear stale failure
            // history, but fall through to the steady-state scheduling below instead of
            // stopping, so this source keeps getting checked once it's due again.
            state.setConsecutiveFailures(0);
            state.setSuspended(false);
            state.setSuspendedReason(null);
        }

        latestJob.ifPresent(job -> {
            boolean terminal = job.getStatus() == JobStatus.SUCCEEDED || job.getStatus() == JobStatus.FAILED;
            boolean alreadyReactedTo = job.getJobId().equals(state.getLastEvaluatedJobId());
            if (!terminal || alreadyReactedTo) {
                return;
            }
            state.setConsecutiveFailures(job.getStatus() == JobStatus.FAILED
                    ? state.getConsecutiveFailures() + 1
                    : 0);
            state.setLastEvaluatedJobId(job.getJobId());
        });

        if (state.getConsecutiveFailures() >= maxConsecutiveFailures) {
            state.setSuspended(true);
            state.setSuspendedReason(
                    "Automatic retry stopped after " + state.getConsecutiveFailures()
                            + " consecutive failed attempts — this likely needs engineering review "
                            + "(the source's underlying site/API may have changed). See GET /v1/jobs?source="
                            + qualifiedName + " for the failure details."
            );
            state.setNextRetryAt(null);
            retryStateService.save(state);
            LOG.warn("Suspending automatic retry for {}: {}", qualifiedName, state.getSuspendedReason());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        // This source's own host may say (via robots.txt Visiting-hours -
        // confirmed live on accessdata.fda.gov, see qara_cli_reg_scraper's
        // robots_policy.py/README) that it isn't fetchable again until
        // later than now — reported as SourceEstimateEntity.nextAvailableAt
        // right after the last real run (Manifest.write_estimate). Trigger
        // anyway and the CLI job would immediately no-op (RobotsDisallowed
        // -> hard_stop) — so this is checked BEFORE the normal due-check
        // below, deferring straight to that time instead of the usual
        // interval, regardless of whether the normal cadence says this
        // tick is "due". null for the vast majority of sources, which
        // fall straight through to the unchanged logic beneath.
        OffsetDateTime nextAvailableAt = estimate.map(SourceEstimateEntity::getNextAvailableAt).orElse(null);
        if (nextAvailableAt != null && nextAvailableAt.isAfter(now)) {
            if (!nextAvailableAt.equals(state.getNextRetryAt())) {
                LOG.info("Deferring automatic check for {} until its host's own crawling window reopens at {}",
                        qualifiedName, nextAvailableAt);
                state.setNextRetryAt(nextAvailableAt);
                retryStateService.save(state);
            }
            return;
        }

        int intervalMinutes = needsWork ? retryIntervalMinutes : steadyStateIntervalMinutes;
        if (state.getNextRetryAt() == null || !state.getNextRetryAt().isAfter(now)) {
            LOG.info("Triggering automatic {} for {} (attempt after {} consecutive failure(s))",
                    needsWork ? "retry" : "steady-state check", qualifiedName, state.getConsecutiveFailures());
            jobService.triggerRetry(qualifiedName, retryIntervalMinutes);
            state.setNextRetryAt(now.plusMinutes(intervalMinutes));
        }
        retryStateService.save(state);
    }
}
