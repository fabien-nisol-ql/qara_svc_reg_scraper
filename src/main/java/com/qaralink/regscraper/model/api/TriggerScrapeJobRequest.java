package com.qaralink.regscraper.model.api;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * Request body for {@code POST /v1/jobs/scrape} — mirrors qara_cli_reg_scraper's
 * own {@code run} flags exactly, so triggering a job here does the same thing
 * as running the CLI by hand with those flags.
 */
@Introspected
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Trigger a scrape job for one or more sources.")
public class TriggerScrapeJobRequest {

    @NotEmpty
    @Schema(description = "\"<regulation>:<source>\" qualified names, e.g. [\"fda:ecfr\", \"fda:recalls\"].", required = true)
    private List<String> sources;

    @Schema(description = "Same as `run --max-new-documents`. Unset means the container's own config.yaml default applies.")
    private Integer maxNewDocuments;

    @Schema(description = "Same as `run --requests-per-second`.")
    private Double requestsPerSecond;

    @Schema(description = "Same as `run --lookback-days` (only meaningful for fda:clearances_510k, fda:recalls).")
    private Integer lookbackDays;
}
