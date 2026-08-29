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

    public static SourceEstimateDTO from(SourceEstimateEntity e) {
        return SourceEstimateDTO.builder()
                .regulation(e.getRegulation())
                .source(e.getSource())
                .computedAt(e.getComputedAt())
                .totalAvailable(e.getTotalAvailable())
                .alreadyKnown(e.getAlreadyKnown())
                .remaining(e.getRemaining())
                .note(e.getNote())
                .build();
    }
}
