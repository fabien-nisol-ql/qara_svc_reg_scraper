package com.qaralink.regscraper.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code GET /v1/retry-state}'s full response — one bulk read (like
 * {@code GET /v1/sources}/{@code GET /v1/status}), not per-source, so a
 * UI makes one call per tab load. Policy values are included so a UI's
 * "how does this work" explainer stays accurate even if the configured
 * interval/threshold changes, rather than being hardcoded client-side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Automatic-retry policy + per-source circuit-breaker state.")
public class RetryStateResponseDTO {

    @Schema(description = "Minutes between automatic retry attempts for the same source while "
            + "it's incomplete or its last attempt failed "
            + "(qaralink.scheduler.retry-interval-minutes) - also the exact retry budget "
            + "handed to the CLI job as QARA_REG_SCRAPER_RETRY_BUDGET_MINUTES.")
    private Integer retryIntervalMinutes;

    @Schema(description = "Minutes between automatic checks once a source is fully caught up "
            + "and its last run succeeded (qaralink.scheduler.steady-state-check-interval-minutes) "
            + "- default 1440, i.e. once a day.")
    private Integer steadyStateIntervalMinutes;

    @Schema(description = "Consecutive failed attempts before automatic retry stops and the "
            + "source is suspended, flagged for engineering review "
            + "(qaralink.scheduler.retry-max-consecutive-failures).")
    private Integer maxConsecutiveFailures;

    private List<SourceRetryStateDTO> sources;
}
