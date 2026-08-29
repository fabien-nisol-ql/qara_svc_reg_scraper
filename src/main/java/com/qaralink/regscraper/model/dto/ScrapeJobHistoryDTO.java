package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.ScrapeJobHistoryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One status transition of a scrape job.")
public class ScrapeJobHistoryDTO {
    private String jobId;
    private JobStatus status;
    private OffsetDateTime changedAt;
    private String displayMessage;
    private String diagnosticMessage;

    public static ScrapeJobHistoryDTO from(ScrapeJobHistoryEntity e) {
        return ScrapeJobHistoryDTO.builder()
                .jobId(e.getJobId())
                .status(e.getStatus())
                .changedAt(e.getChangedAt())
                .displayMessage(e.getDisplayMessage())
                .diagnosticMessage(e.getDiagnosticMessage())
                .build();
    }
}
