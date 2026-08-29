package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.ScrapeJobEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A triggered scrape job's status — manual ({@code POST /v1/jobs/scrape}) or
 * scheduled. New to this service; qara_cli_reg_scraper itself has no
 * equivalent, it only knows about its own single {@link ScrapeRunDTO}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Status of a triggered scrape job (Docker container or Kubernetes Job).")
public class ScrapeJobDTO {

    private String jobId;
    @Schema(description = "The \"<regulation>:<source>\" qualified names this job scrapes.")
    private List<String> sources;
    private JobStatus status;
    @Schema(example = "docker | kubernetes")
    private String provider;
    @Schema(example = "manual | scheduler")
    private String triggeredBy;
    private OffsetDateTime submittedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Integer exitCode;
    private String displayMessage;
    private String diagnosticMessage;

    public static ScrapeJobDTO from(ScrapeJobEntity e) {
        return ScrapeJobDTO.builder()
                .jobId(e.getJobId())
                .sources(e.getSources())
                .status(e.getStatus())
                .provider(e.getProvider())
                .triggeredBy(e.getTriggeredBy())
                .submittedAt(e.getSubmittedAt())
                .startedAt(e.getStartedAt())
                .finishedAt(e.getFinishedAt())
                .exitCode(e.getExitCode())
                .displayMessage(e.getDisplayMessage())
                .diagnosticMessage(e.getDiagnosticMessage())
                .build();
    }
}
