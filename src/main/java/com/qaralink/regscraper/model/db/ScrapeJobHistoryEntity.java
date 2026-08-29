package com.qaralink.regscraper.model.db;

import com.qaralink.regscraper.model.dto.JobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One row per status transition of a {@link ScrapeJobEntity} — the "history"
 * counterpart, same idea as {@code IndexingJobHistoryEntity} in opc_svc_ai.
 */
@Entity
@Table(name = "scrape_job_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeJobHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "display_message")
    private String displayMessage;

    @Column(name = "diagnostic_message", length = 4096)
    private String diagnosticMessage;
}
