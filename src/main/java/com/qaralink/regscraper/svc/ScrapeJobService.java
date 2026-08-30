package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.exceptions.BotBlockCooldownActiveException;
import com.qaralink.regscraper.model.api.TriggerScrapeJobRequest;
import com.qaralink.regscraper.model.db.ScrapeJobEntity;
import com.qaralink.regscraper.model.db.ScrapeJobHistoryEntity;
import com.qaralink.regscraper.model.db.SourceRetryStateEntity;
import com.qaralink.regscraper.model.db.repo.ScrapeJobHistoryRepository;
import com.qaralink.regscraper.model.db.repo.ScrapeJobRepository;
import com.qaralink.regscraper.model.dto.JobStatus;
import com.qaralink.regscraper.model.dto.ScrapeJobDTO;
import com.qaralink.regscraper.svc.workload.Workload;
import com.qaralink.regscraper.svc.workload.WorkloadOrchestrator;
import com.qaralink.regscraper.svc.workload.WorkloadResult;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Triggers a scrape job (manual REST call or {@code ScraperAutoRunScheduler})
 * by building a {@link Workload} — one full {@code qara-reg-scraper run}
 * invocation — and submitting it via whichever {@link WorkloadOrchestrator}
 * is active ({@code qaralink.execution.provider}: docker | kubernetes),
 * tracking its status/history in {@link ScrapeJobEntity}/
 * {@link ScrapeJobHistoryEntity}.
 */
@Singleton
public class ScrapeJobService {

    private static final Logger LOG = LoggerFactory.getLogger(ScrapeJobService.class);
    private static final String WORKLOAD_TYPE = "ScraperRun";

    private final ScrapeJobRepository jobRepository;
    private final ScrapeJobHistoryRepository historyRepository;
    private final WorkloadOrchestrator orchestrator;
    private final String executionProvider;
    private final SourceRetryStateService retryStateService;
    private final int botBlockCooldownHours;

    public ScrapeJobService(
            ScrapeJobRepository jobRepository,
            ScrapeJobHistoryRepository historyRepository,
            WorkloadOrchestrator orchestrator,
            @Value("${qaralink.execution.provider}") String executionProvider,
            SourceRetryStateService retryStateService,
            @Value("${qaralink.scheduler.bot-block-cooldown-hours}") int botBlockCooldownHours
    ) {
        this.jobRepository = jobRepository;
        this.historyRepository = historyRepository;
        this.orchestrator = orchestrator;
        this.executionProvider = executionProvider;
        this.retryStateService = retryStateService;
        this.botBlockCooldownHours = botBlockCooldownHours;
    }

    public ScrapeJobDTO trigger(TriggerScrapeJobRequest request, String triggeredBy) {
        String jobId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        if ("manual".equals(triggeredBy)) {
            // Checked BEFORE anything else below (a separate pass over every requested
            // source, not folded into the reset loop) so a multi-source trigger request
            // fails atomically — refusing ONE still-cooling-down source must not have
            // already reset a sibling source's own circuit breaker first. Not an absolute
            // block: overrideBotBlockCooldown lets a human retry early anyway (the UI only
            // sets it after showing a clear warning that this may extend how long the block
            // lasts) — without it, this is the default, safe path for every OTHER caller
            // (a scheduled job, a direct API call, a UI bug) that isn't a human who's just
            // explicitly confirmed they understand the risk.
            if (!Boolean.TRUE.equals(request.getOverrideBotBlockCooldown())) {
                for (String qualifiedName : request.getSources()) {
                    String[] parts = qualifiedName.split(":", 2);
                    if (parts.length != 2) {
                        continue;
                    }
                    Optional<SourceRetryStateEntity> state = retryStateService.find(parts[0], parts[1]);
                    state.filter(SourceRetryStateEntity::getSuspended)
                            .filter(SourceRetryStateEntity::getSuspendedDueToBotBlock)
                            .filter(s -> s.getSuspendedAt() != null)
                            .ifPresent(s -> {
                                OffsetDateTime retryAllowedAt = s.getSuspendedAt().plusHours(botBlockCooldownHours);
                                if (now.isBefore(retryAllowedAt)) {
                                    long hoursLeft = java.time.Duration.between(now, retryAllowedAt).toHours() + 1;
                                    throw new BotBlockCooldownActiveException(
                                            qualifiedName + " was suspended after detecting a bot-management block "
                                                    + "and can't be manually retried yet — continuing to probe a host "
                                                    + "during an active block plausibly keeps it from ever clearing. "
                                                    + "Wait " + hoursLeft + " more hour" + (hoursLeft == 1 ? "" : "s")
                                                    + " (cooldown: " + botBlockCooldownHours + "h from "
                                                    + s.getSuspendedAt() + "), or retry now with an explicit override."
                                    );
                                }
                            });
                }
            } else {
                LOG.warn("Manual retry for {} overrides an active bot-block cooldown (explicit human confirmation)",
                        request.getSources());
            }

            // A human re-triggering by hand is the one thing that un-sticks
            // the retry circuit breaker (see SourceRetryScheduler) — NOT the
            // daily ScraperAutoRunScheduler cron (that's routine, not a human
            // having actually looked at a suspended source's problem).
            for (String qualifiedName : request.getSources()) {
                String[] parts = qualifiedName.split(":", 2);
                if (parts.length == 2) {
                    retryStateService.resetToHealthy(parts[0], parts[1]);
                }
            }
        }

        ScrapeJobEntity job = ScrapeJobEntity.builder()
                .jobId(jobId)
                .sources(request.getSources())
                .status(JobStatus.PENDING)
                .provider(executionProvider)
                .triggeredBy(triggeredBy)
                .submittedAt(now)
                .build();
        jobRepository.save(job);
        recordHistory(jobId, JobStatus.PENDING, "Job submitted", null, now);

        Workload workload = Workload.builder()
                .type(WORKLOAD_TYPE)
                .id(jobId)
                .args(buildArgs(request))
                .annotation("qaralink.reg-scraper/triggered-by", triggeredBy)
                .annotation("qaralink.reg-scraper/triggered-at", now.toString())
                .build();

        try {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            jobRepository.update(job);
            recordHistory(jobId, JobStatus.RUNNING, "Workload submitted to " + executionProvider, null, job.getStartedAt());

            orchestrator.submitWorkloadAndProcess(workload, this::onWorkloadComplete)
                    .exceptionally(throwable -> {
                        onWorkloadFailed(jobId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOG.error("Failed to submit scrape job {}", jobId, e);
            onWorkloadFailed(jobId, e);
        }

        return ScrapeJobDTO.from(jobRepository.findById(jobId).orElse(job));
    }

    /** Sentinel `sources` value for a sync-sources job — not a real
     * "<regulation>:<source>" qualified name, so ScrapeJobRepository#search's
     * `LIKE '%"<source>"%'` filter harmlessly never matches it against a
     * real source filter. */
    private static final List<String> SOURCE_SYNC_SENTINEL = List.of("__sync_sources__");

    /**
     * Launches `qara-reg-scraper sync-sources` — same image/{@link #WORKLOAD_TYPE}
     * as a real scrape job (it's the same CLI, just a different
     * subcommand; no separate `qaralink.workloads.*` image config exists
     * or is needed), tracked the same way in {@link ScrapeJobEntity}/history
     * so it shows up in GET /v1/jobs — distinguishable from a real scrape
     * job by its sentinel {@code sources} value, not a different workload
     * type. See qara_cli_reg_scraper's docs/source-registry-sync.md.
     */
    public ScrapeJobDTO triggerSourceSync(String triggeredBy) {
        String jobId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        ScrapeJobEntity job = ScrapeJobEntity.builder()
                .jobId(jobId)
                .sources(SOURCE_SYNC_SENTINEL)
                .status(JobStatus.PENDING)
                .provider(executionProvider)
                .triggeredBy(triggeredBy)
                .submittedAt(now)
                .build();
        jobRepository.save(job);
        recordHistory(jobId, JobStatus.PENDING, "Source-sync job submitted", null, now);

        Workload workload = Workload.builder()
                .type(WORKLOAD_TYPE)
                .id(jobId)
                .args("sync-sources --quiet")
                .annotation("qaralink.reg-scraper/triggered-by", triggeredBy)
                .annotation("qaralink.reg-scraper/triggered-at", now.toString())
                .build();

        try {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            jobRepository.update(job);
            recordHistory(jobId, JobStatus.RUNNING, "Workload submitted to " + executionProvider, null, job.getStartedAt());

            orchestrator.submitWorkloadAndProcess(workload, this::onWorkloadComplete)
                    .exceptionally(throwable -> {
                        onWorkloadFailed(jobId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOG.error("Failed to submit source-sync job {}", jobId, e);
            onWorkloadFailed(jobId, e);
        }

        return ScrapeJobDTO.from(jobRepository.findById(jobId).orElse(job));
    }

    /**
     * Launches a catch-up-and-retry run for exactly one source — used only
     * by {@link com.qaralink.regscraper.scheduler.SourceRetryScheduler}.
     * Same {@link #WORKLOAD_TYPE}/tracking as {@link #trigger}, but always
     * `--max-new-documents -1` (the whole point of retrying automatically
     * is catching the backlog all the way up, not another budgeted
     * partial run) and carries {@code retryBudgetMinutes} as
     * {@code QARA_REG_SCRAPER_RETRY_BUDGET_MINUTES} via {@link Workload#getEnv()}
     * so the CLI's own in-process backoff (see qara_cli_reg_scraper's
     * docs/retry-and-backlog-catchup.md) is bounded by the exact interval
     * the scheduler will wait before trying again anyway.
     */
    public ScrapeJobDTO triggerRetry(String qualifiedName, int retryBudgetMinutes) {
        String jobId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        String triggeredBy = "retry-scheduler";

        ScrapeJobEntity job = ScrapeJobEntity.builder()
                .jobId(jobId)
                .sources(List.of(qualifiedName))
                .status(JobStatus.PENDING)
                .provider(executionProvider)
                .triggeredBy(triggeredBy)
                .submittedAt(now)
                .build();
        jobRepository.save(job);
        recordHistory(jobId, JobStatus.PENDING, "Retry job submitted", null, now);

        Workload workload = Workload.builder()
                .type(WORKLOAD_TYPE)
                .id(jobId)
                .args("run --source " + qualifiedName + " --max-new-documents -1 --quiet")
                .env(Map.of("QARA_REG_SCRAPER_RETRY_BUDGET_MINUTES", String.valueOf(retryBudgetMinutes)))
                .annotation("qaralink.reg-scraper/triggered-by", triggeredBy)
                .annotation("qaralink.reg-scraper/triggered-at", now.toString())
                .build();

        try {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            jobRepository.update(job);
            recordHistory(jobId, JobStatus.RUNNING, "Workload submitted to " + executionProvider, null, job.getStartedAt());

            orchestrator.submitWorkloadAndProcess(workload, this::onWorkloadComplete)
                    .exceptionally(throwable -> {
                        onWorkloadFailed(jobId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOG.error("Failed to submit retry job {}", jobId, e);
            onWorkloadFailed(jobId, e);
        }

        return ScrapeJobDTO.from(jobRepository.findById(jobId).orElse(job));
    }

    private String buildArgs(TriggerScrapeJobRequest request) {
        StringBuilder args = new StringBuilder("run --source ").append(String.join(",", request.getSources()));
        if (request.getMaxNewDocuments() != null) {
            args.append(" --max-new-documents ").append(request.getMaxNewDocuments());
        }
        if (request.getRequestsPerSecond() != null) {
            args.append(" --requests-per-second ").append(request.getRequestsPerSecond());
        }
        if (request.getLookbackDays() != null) {
            args.append(" --lookback-days ").append(request.getLookbackDays());
        }
        args.append(" --quiet");
        return args.toString();
    }

    private Void onWorkloadComplete(WorkloadResult result) {
        String jobId = result.getWorkload().getId();
        JobStatus status = result.isSuccess() ? JobStatus.SUCCEEDED : JobStatus.FAILED;
        OffsetDateTime now = OffsetDateTime.now();

        if (status == JobStatus.SUCCEEDED) {
            LOG.info("Scrape job {} succeeded: {}", jobId, result.getDisplayMessage());
        } else {
            LOG.warn("Scrape job {} finished with a failing exit code: {} | {}", jobId, result.getDisplayMessage(), result.getDiagnosticMessage());
        }

        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setFinishedAt(now);
            job.setExitCode((int) result.getExitCode());
            job.setDisplayMessage(result.getDisplayMessage());
            job.setDiagnosticMessage(result.getDiagnosticMessage());
            jobRepository.update(job);
        });
        recordHistory(jobId, status, result.getDisplayMessage(), result.getDiagnosticMessage(), now);
        return null;
    }

    private void onWorkloadFailed(String jobId, Throwable throwable) {
        OffsetDateTime now = OffsetDateTime.now();
        String diagnostic = throwable == null ? "unknown error" : throwable.getMessage();
        LOG.error("Scrape job {} failed before/during submission", jobId, throwable);
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(now);
            job.setDisplayMessage("Job failed before/during submission");
            job.setDiagnosticMessage(diagnostic);
            jobRepository.update(job);
        });
        recordHistory(jobId, JobStatus.FAILED, "Job failed before/during submission", diagnostic, now);
    }

    private void recordHistory(String jobId, JobStatus status, String displayMessage, String diagnosticMessage, OffsetDateTime changedAt) {
        historyRepository.save(ScrapeJobHistoryEntity.builder()
                .jobId(jobId)
                .status(status)
                .changedAt(changedAt)
                .displayMessage(displayMessage)
                .diagnosticMessage(diagnosticMessage)
                .build());
    }

    public Optional<ScrapeJobDTO> get(String jobId) {
        return jobRepository.findById(jobId).map(ScrapeJobDTO::from);
    }

    public Page<ScrapeJobDTO> list(Pageable pageable) {
        return search(null, pageable);
    }

    /** @param source Optional "<regulation>:<source>" qualified name filter — see
     * ScrapeJobRepository#search for why a job can't just be equality-matched on it. */
    public Page<ScrapeJobDTO> search(String source, Pageable pageable) {
        return jobRepository.search(source, pageable).map(ScrapeJobDTO::from);
    }

    public Page<com.qaralink.regscraper.model.dto.ScrapeJobHistoryDTO> history(String jobId, Pageable pageable) {
        return historyRepository.findAllByJobIdOrderByChangedAtDesc(jobId, pageable)
                .map(com.qaralink.regscraper.model.dto.ScrapeJobHistoryDTO::from);
    }
}
