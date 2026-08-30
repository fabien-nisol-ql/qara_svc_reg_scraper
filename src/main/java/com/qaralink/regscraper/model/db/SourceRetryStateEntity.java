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

    // When `suspended` first flipped to true - lets a UI show how long a
    // source has been sitting suspended, not just that it currently is.
    // See V8's own migration comment for the full "why", and
    // SourceRetryScheduler for where this actually gets set/cleared.
    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    // Distinguishes a bot-block-triggered suspension (immediate, first
    // occurrence) from the generic consecutive-failures one - see V9's own
    // migration comment. ScrapeJobService.trigger reads this to enforce
    // qaralink.scheduler.bot-block-cooldown-hours before even a MANUAL
    // retry is allowed, ONLY for this suspension kind.
    @Column(name = "suspended_due_to_bot_block", nullable = false)
    @Builder.Default
    private Boolean suspendedDueToBotBlock = false;

    @Column(name = "last_evaluated_job_id")
    private String lastEvaluatedJobId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
