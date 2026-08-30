package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.SourceRetryStateEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One entry in {@code GET /v1/retry-state}'s {@code sources} list — see
 * {@link RetryStateResponseDTO} for the full response shape (this DTO
 * carries no policy fields of its own; those are top-level on the
 * response, shared across every source). A source with no
 * {@link SourceRetryStateEntity} row yet (never evaluated) still gets a
 * row here with defaults, via {@link #defaultFor} — see
 * {@code RetryStateService}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The automatic-retry circuit-breaker state for one source.")
public class SourceRetryStateDTO {

    @Schema(example = "fda", requiredMode = Schema.RequiredMode.REQUIRED)
    private String regulation;

    @Schema(example = "classification", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "When the next automatic retry is scheduled - null if the source is "
            + "fully caught up (nothing to retry) or suspended (see `suspended` below).")
    private OffsetDateTime nextRetryAt;

    @Schema(description = "How many attempts in a row have failed since the source last "
            + "succeeded or was manually retried.")
    private Integer consecutiveFailures;

    @Schema(description = "true once consecutiveFailures reached the configured threshold - "
            + "automatic retries have stopped; a human needs to look at suspendedReason. "
            + "Manually triggering POST /v1/jobs/scrape for this source clears this.")
    private Boolean suspended;

    @Schema(description = "Why automatic retries stopped, set only when suspended is true.")
    private String suspendedReason;

    @Schema(description = "When suspension began, set only when suspended is true - lets a UI "
            + "show a human how long this has been sitting suspended (e.g. \"blocked for 47 "
            + "minutes\"), which matters most for a bot-block-triggered suspension (immediate, "
            + "not after several failures - see SourceRetryScheduler) since deciding when it's "
            + "safe to manually retry needs a real sense of how stale this state is.")
    private OffsetDateTime suspendedAt;

    @Schema(description = "true when suspended was caused by a detected bot-management block "
            + "(immediate, on the very first occurrence) rather than the generic "
            + "consecutive-failures threshold. This suspension kind has its own manual-retry "
            + "cooldown (qaralink.scheduler.bot-block-cooldown-hours, see "
            + "RetryStateResponseDTO#botBlockCooldownHours) - POST /v1/jobs/scrape is refused, "
            + "not just discouraged, until it elapses.")
    private Boolean suspendedDueToBotBlock;

    public static SourceRetryStateDTO from(SourceRetryStateEntity e) {
        return SourceRetryStateDTO.builder()
                .regulation(e.getRegulation())
                .source(e.getSource())
                .nextRetryAt(e.getNextRetryAt())
                .consecutiveFailures(e.getConsecutiveFailures())
                .suspended(e.getSuspended())
                .suspendedReason(e.getSuspendedReason())
                .suspendedAt(e.getSuspendedAt())
                .suspendedDueToBotBlock(e.getSuspendedDueToBotBlock())
                .build();
    }

    /** A source with no retry-state row yet - never evaluated by the scheduler
     * (e.g. registered moments ago) - still gets a sensible default row rather
     * than being omitted from the response entirely. */
    public static SourceRetryStateDTO defaultFor(String regulation, String source) {
        return SourceRetryStateDTO.builder()
                .regulation(regulation)
                .source(source)
                .nextRetryAt(null)
                .consecutiveFailures(0)
                .suspended(false)
                .suspendedReason(null)
                .suspendedDueToBotBlock(false)
                .build();
    }
}
