package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.SourceEstimateEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Latest known "what's left to do" snapshot for one (regulation, source)
 * pair — mirrors qara_cli_reg_scraper's manifest {@code estimate.json} file
 * (written by {@code Manifest.write_estimate}, right after a real run).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Latest 'what's left to do' snapshot for one source.")
public class SourceEstimateDTO {

    @Schema(required = true)
    private String regulation;
    @Schema(required = true)
    private String source;
    private OffsetDateTime computedAt;
    private Integer totalAvailable;
    private Integer alreadyKnown;
    private Integer remaining;
    private String note;
    @Schema(description = "The next UTC time this source's own host says (robots.txt Visiting-hours) "
            + "it's next fetchable - null for the vast majority of sources (no such restriction "
            + "declared, or fetchable right now either way). See qara_cli_reg_scraper's PreviewInfo "
            + "and README for where this comes from.")
    private OffsetDateTime nextAvailableAt;
    @Schema(description = "A human-readable description of the RECURRING window itself (e.g. "
            + "\"11:00 PM-5:00 AM America/New York\"), not just the one future instant "
            + "nextAvailableAt is - present regardless of whether that window happens to be open "
            + "right now. Null whenever nextAvailableAt's own source-side computation is.")
    private String nextAvailableNote;

    public static SourceEstimateDTO from(SourceEstimateEntity e) {
        return SourceEstimateDTO.builder()
                .regulation(e.getRegulation())
                .source(e.getSource())
                .computedAt(e.getComputedAt())
                .totalAvailable(e.getTotalAvailable())
                .alreadyKnown(e.getAlreadyKnown())
                .remaining(e.getRemaining())
                .note(e.getNote())
                .nextAvailableAt(e.getNextAvailableAt())
                .nextAvailableNote(e.getNextAvailableNote())
                .build();
    }
}
