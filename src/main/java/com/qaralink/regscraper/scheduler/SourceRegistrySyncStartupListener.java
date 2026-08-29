package com.qaralink.regscraper.scheduler;

import com.qaralink.regscraper.svc.ScrapeJobService;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launches a `sync-sources`-only CLI job ({@link ScrapeJobService#triggerSourceSync})
 * once, whenever this service starts — the first of the two triggers that
 * keep the {@code regulation_source} table current without a hand-
 * maintained mirror (the other is every real scrape job's own graceful
 * push; see {@code ScraperAutoRunScheduler} for the daily cron that also
 * ends up triggering that). See qara_cli_reg_scraper's
 * docs/source-registry-sync.md for the full picture.
 * <p>
 * First use of {@link ApplicationEventListener}/{@link StartupEvent} in
 * this codebase (confirmed via grep — no existing convention to mirror).
 * {@link ScrapeJobService#triggerSourceSync} itself only submits the
 * workload asynchronously (same as {@link ScrapeJobService#trigger}) and
 * this listener wraps that in its own try/catch on top — a failure here
 * must never block or fail this service's own startup, only get logged.
 */
@Singleton
public class SourceRegistrySyncStartupListener implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SourceRegistrySyncStartupListener.class);

    private final ScrapeJobService jobService;

    public SourceRegistrySyncStartupListener(ScrapeJobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        try {
            LOG.info("Triggering source registry sync on startup");
            jobService.triggerSourceSync("startup");
        } catch (Exception e) {
            LOG.warn("Failed to trigger startup source registry sync (service startup continues regardless)", e);
        }
    }
}
