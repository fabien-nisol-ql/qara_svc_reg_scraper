package com.qaralink.regscraper.scheduler;

import com.qaralink.regscraper.model.api.TriggerScrapeJobRequest;
import com.qaralink.regscraper.svc.ScrapeJobService;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Automatically triggers a scrape job for the configured sources on a
 * schedule, sharing the exact same submission path as a manual
 * {@code POST /v1/jobs/scrape} call ({@link ScrapeJobService#trigger}) —
 * so a scheduled run and a manual one are indistinguishable except for
 * {@code triggeredBy}.
 * <p>
 * One overall cron expression (not per-source, unlike qara_cli_reg_scraper's
 * own {@code docker/crontab}) covering every configured source in a single
 * job — {@code qaralink.execution.provider}'s Docker/Kubernetes job
 * already runs them one at a time internally via the CLI's own
 * {@code --source a,b,c} comma-list and per-run budget pacing, so there's
 * no need to stagger separate jobs the way the old in-container
 * supercronic schedule did.
 */
@Singleton
public class ScraperAutoRunScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ScraperAutoRunScheduler.class);

    private final ScrapeJobService jobService;
    private final String sourcesConfig;

    public ScraperAutoRunScheduler(
            ScrapeJobService jobService,
            @Value("${qaralink.scheduler.sources:}") String sourcesConfig
    ) {
        this.jobService = jobService;
        this.sourcesConfig = sourcesConfig;
    }

    @Scheduled(cron = "${qaralink.scheduler.cron:0 0 3 * * *}")
    void triggerScheduledRun() {
        List<String> sources = Arrays.stream(sourcesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (sources.isEmpty()) {
            LOG.debug("qaralink.scheduler.sources is empty — nothing to auto-run");
            return;
        }

        LOG.info("Auto-triggering scheduled scrape job for {}", sources);
        jobService.trigger(TriggerScrapeJobRequest.builder().sources(sources).build(), "scheduler");
    }
}
