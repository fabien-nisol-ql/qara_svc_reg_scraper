package com.qaralink.regscraper.model.db;

import com.qaralink.regscraper.model.db.converters.ErrorDetailsConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * One row per completed (or in-progress) scrape run — one process
 * invocation of {@code qara-reg-scraper run --source <regulation>:<source>}.
 * Mirrors qara_cli_reg_scraper's {@code ScrapeRun} SQLAlchemy model and the
 * manifest's {@code _manifest/runs/<run_id>.json} file.
 */
@Entity
@Table(name = "scrape_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeRunEntity {

    @Id
    @Column(name = "run_id")
    private String runId;

    @Column(nullable = false)
    private String regulation;

    @Column(nullable = false)
    private String source;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(nullable = false)
    private String status;

    @Builder.Default
    private Integer checked = 0;

    // Field named newCount (not "new", a Java keyword) but mapped to the
    // "new" SQL column, matching the Python model's field name exactly.
    @Column(name = "new", nullable = false)
    @Builder.Default
    private Integer newCount = 0;

    @Builder.Default
    private Integer updated = 0;

    @Builder.Default
    private Integer unchanged = 0;

    @Builder.Default
    private Integer errors = 0;

    @Convert(converter = ErrorDetailsConverter.class)
    @Column(name = "error_details", nullable = false)
    @Builder.Default
    private List<Map<String, String>> errorDetails = List.of();

    // Mirrors qara_cli_reg_scraper's RunSummary.stop_reason ("completed" |
    // "budget_reached" | "hard_stop" | "bot_block") - see V7's own
    // migration comment for why this was added and what reads it
    // (SourceRetryScheduler, to react to a detected bot-management block
    // immediately rather than waiting on the generic consecutive-failure
    // threshold). NULL for any run pushed before this column existed.
    @Column(name = "stop_reason")
    private String stopReason;
}
