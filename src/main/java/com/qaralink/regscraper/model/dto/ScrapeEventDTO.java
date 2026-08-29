package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.ScrapeEventEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One manifest event (new/updated/unchanged/error) for one document, one
 * run. Mirrors qara_cli_reg_scraper's manifest
 * {@code _manifest/events/<yyyy>/<mm>/<dd>/*.json} files.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One per-document event recorded during a scrape run.")
public class ScrapeEventDTO {

    private String runId;
    @Schema(required = true)
    private String regulation;
    @Schema(required = true)
    private String source;
    @Schema(required = true)
    private String documentId;
    @Schema(example = "new | updated | unchanged | error | skipped_disallowed", required = true)
    private String event;
    private OffsetDateTime ts;
    private String url;
    private Integer httpStatus;
    private String contentHash;
    private String storagePath;
    private String error;

    public static ScrapeEventDTO from(ScrapeEventEntity e) {
        return ScrapeEventDTO.builder()
                .runId(e.getRunId())
                .regulation(e.getRegulation())
                .source(e.getSource())
                .documentId(e.getDocumentId())
                .event(e.getEvent())
                .ts(e.getTs())
                .url(e.getUrl())
                .httpStatus(e.getHttpStatus())
                .contentHash(e.getContentHash())
                .storagePath(e.getStoragePath())
                .error(e.getError())
                .build();
    }
}
