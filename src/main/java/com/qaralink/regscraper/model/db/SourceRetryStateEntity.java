package com.qaralink.regscraper.model.db;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * The automatic-retry circuit-breaker state for one (regulation, source)
 * pair — owned entirely by {@link com.qaralink.regscraper.scheduler.SourceRetryScheduler},
 * unlike {@link RegulationSourceEntity}/{@link SourceEstimateEntity} which
 * qara_cli_reg_scraper itself pushes. See that scheduler's own docstring
 * for the full state machine.
 */
@Entity
@Table(name = "source_retry_state", uniqueConstraints = @UniqueConstraint(
        name = "uq_source_retry_state", columnNames = {"regulation", "source"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceRetryStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String regulation;

    @Column(nullable = false)
    private String source;

    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean suspended = false;

    @Column(name = "suspended_reason")
    private String suspendedReason;

    @Column(name = "last_evaluated_job_id")
    private String lastEvaluatedJobId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
