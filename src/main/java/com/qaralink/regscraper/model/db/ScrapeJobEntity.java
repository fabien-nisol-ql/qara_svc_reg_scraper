package com.qaralink.regscraper.model.db;

import com.qaralink.regscraper.model.db.converters.StringListConverter;
import com.qaralink.regscraper.model.dto.JobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One row per triggered scrape job (a manual {@code POST /v1/jobs/scrape}
 * call, or the scheduler) — tracks its Docker/Kubernetes execution. New to
 * this service; the CLI itself has no equivalent concept, it only knows
 * about its own single run (see {@link ScrapeRunEntity}, which this job
 * eventually produces one of, per source, once the container actually
 * runs).
 */
@Entity
@Table(name = "scrape_job")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeJobEntity {

    @Id
    @Column(name = "job_id")
    private String jobId;

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false)
    private List<String> sources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    /** "docker" | "kubernetes" — whichever WorkloadOrchestrator ran this job. */
    @Column(nullable = false)
    private String provider;

    /** "manual" | "scheduler" */
    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "display_message")
    private String displayMessage;

    @Column(name = "diagnostic_message", length = 4096)
    private String diagnosticMessage;
}
