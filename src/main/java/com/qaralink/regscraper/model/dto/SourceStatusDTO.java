package com.qaralink.regscraper.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One aggregated row per (regulation, source) — documents count + latest
 * run + latest estimate, in one place. Backs {@code GET /v1/status}; same
 * shape as qara_cli_reg_scraper's own (soon-to-be-retired) {@code status}
 * CLI command output.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated status for one source: document count, latest run, latest estimate.")
public class SourceStatusDTO {

    private String regulation;
    private String source;
    private long documents;

    private String lastRunId;
    private OffsetDateTime lastFinishedAt;
    private String lastStatus;
    private Integer lastErrors;

    private Integer totalAvailable;
    private Integer remaining;
    private String estimateNote;
}
