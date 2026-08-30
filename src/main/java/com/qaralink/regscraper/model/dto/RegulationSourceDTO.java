package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.RegulationSourceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One entry in GET /v1/sources / the request body of PUT /v1/sources
 * (a bulk LIST of these, not a single one — see RegulationSourceService).
 * Backed by the {@code regulation_source} table since this table was
 * introduced: this service has no way to discover, on its own, what
 * sources qara_cli_reg_scraper knows how to run (see StatusController's
 * own docstring on the same gap) — the CLI pushes this list itself, on
 * service startup and on every real scrape run (see that repo's
 * docs/source-registry-sync.md). The old alternative
 * (RegulationSourceRegistry.java, a hand-maintained mirror of the CLI's
 * own registry) is gone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One source qara_cli_reg_scraper knows how to run, addressed as \"<regulation>:<source>\".")
public class RegulationSourceDTO {

    @Schema(description = "Row id. Absent/null on a PUT /v1/sources request body; always present on a "
            + "GET /v1/sources response.")
    private Long id;

    @Schema(example = "fda", requiredMode = Schema.RequiredMode.REQUIRED)
    private String regulation;

    @Schema(example = "ecfr", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "Short display name.", example = "eCFR")
    private String label;

    @Schema(description = "What this source covers.", example = "21 CFR medical device regulations")
    private String description;

    @Schema(description = "Whether this source is enabled to run at all (qara_cli_reg_scraper's own "
            + "config.yaml SourceSettings.enabled).")
    private Boolean enabled;

    @Schema(description = "Effective requests-per-second pacing this source runs at — the config.yaml "
            + "per-source override if set, else the global default.", example = "1.0")
    private Double requestsPerSecond;

    @Schema(description = "Effective cap on how many new documents one run fetches before stopping — "
            + "the config.yaml per-source override if set, else the global default. Null means unlimited "
            + "(config.yaml's -1, normalized).", example = "1000")
    private Integer maxNewDocumentsPerRun;

    @Schema(description = "How many days after first capturing a document this source re-verifies it. "
            + "Null is a real, meaningful value — this source never re-checks an already-captured "
            + "document, not \"not synced yet\".", example = "14")
    private Integer recheckAfterDays;

    @Schema(description = "How many days back a lookback-windowed source (e.g. \"everything decided in "
            + "the last N days\") looks. Null is a real, meaningful value — this source isn't "
            + "lookback-windowed at all, not \"not synced yet\".", example = "30")
    private Integer lookbackDays;

    @Schema(description = "When this row was last confirmed by a sync from the CLI — server-assigned, "
            + "ignored if set on a PUT request body. Absent on a request body, always present on a read.")
    private OffsetDateTime syncedAt;

    public static RegulationSourceDTO from(RegulationSourceEntity e) {
        return RegulationSourceDTO.builder()
                .id(e.getId())
                .regulation(e.getRegulation())
                .source(e.getSource())
                .label(e.getLabel())
                .description(e.getDescription())
                .enabled(e.getEnabled())
                .requestsPerSecond(e.getRequestsPerSecond())
                .maxNewDocumentsPerRun(e.getMaxNewDocumentsPerRun())
                .recheckAfterDays(e.getRecheckAfterDays())
                .lookbackDays(e.getLookbackDays())
                .syncedAt(e.getSyncedAt())
                .build();
    }
}
