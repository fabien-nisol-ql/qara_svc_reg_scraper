package com.qaralink.regscraper.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.qaralink.regscraper.model.db.ScrapeRunEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * One scrape run's summary — mirrors qara_cli_reg_scraper's manifest
 * {@code _manifest/runs/<run_id>.json} file and {@code RunSummary}
 * dataclass. Upserted once at the start of a run (status "running") and
 * again at the end ({@code finalize()}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One scrape run's summary.")
public class ScrapeRunDTO {

    @Schema(required = true)
    private String runId;
    @Schema(required = true)
    private String regulation;
    @Schema(required = true)
    private String source;

    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    @Schema(example = "running | success | partial_failure | failed", required = true)
    private String status;

    @Builder.Default
    private Integer checked = 0;
    // Field named new_ (not "new", a Java keyword) but serialized on the
    // wire as "new" to match qara_cli_reg_scraper's own field name exactly.
    @JsonProperty("new")
    @Builder.Default
    private Integer new_ = 0;
    @Builder.Default
    private Integer updated = 0;
    @Builder.Default
    private Integer unchanged = 0;
    @Builder.Default
    private Integer errors = 0;
    @Builder.Default
    private List<Map<String, String>> errorDetails = List.of();

    public static ScrapeRunDTO from(ScrapeRunEntity e) {
        return ScrapeRunDTO.builder()
                .runId(e.getRunId())
                .regulation(e.getRegulation())
                .source(e.getSource())
                .startedAt(e.getStartedAt())
                .finishedAt(e.getFinishedAt())
                .status(e.getStatus())
                .checked(e.getChecked())
                .new_(e.getNewCount())
                .updated(e.getUpdated())
                .unchanged(e.getUnchanged())
                .errors(e.getErrors())
                .errorDetails(e.getErrorDetails())
                .build();
    }
}
