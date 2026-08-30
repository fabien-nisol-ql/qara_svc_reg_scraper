package com.qaralink.regscraper.exceptions;

import com.qaralink.mn.exceptions.HttpExceptionMapping;
import io.micronaut.http.HttpStatus;

/**
 * Thrown by {@link com.qaralink.regscraper.svc.ScrapeJobService#trigger} when a manual
 * retry (POST /v1/jobs/scrape) is attempted for a source suspended due to a detected
 * bot-management block before qaralink.scheduler.bot-block-cooldown-hours has elapsed
 * since {@link com.qaralink.regscraper.model.db.SourceRetryStateEntity#getSuspendedAt()}.
 * Enforced here, not just as a disabled button client-side — a human is just as capable
 * of resetting/extending a reputation-based block's cooldown as an automatic retry is
 * (confirmed live, 2026-08-30), so this can't be a UI-only convention.
 */
@HttpExceptionMapping(status = HttpStatus.CONFLICT)
public class BotBlockCooldownActiveException extends RuntimeException {
    public BotBlockCooldownActiveException(String message) {
        super(message);
    }
}
