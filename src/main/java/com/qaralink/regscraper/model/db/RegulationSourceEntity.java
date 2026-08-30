package com.qaralink.regscraper.model.db;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One source qara_cli_reg_scraper knows how to run, addressed as
 * "{regulation}:{source}" - replaces the old hand-maintained
 * RegulationSourceRegistry.java list. Kept in sync entirely by the CLI
 * itself via PUT /v1/sources (see RegulationSourceService#replaceAll and
 * qara_cli_reg_scraper's docs/source-registry-sync.md) - this service
 * never invents or edits a row's label/description, only stores what it's
 * told and drops what it's not told about anymore.
 */
@Entity
@Table(name = "regulation_source", uniqueConstraints = @UniqueConstraint(
        name = "uq_regulation_source", columnNames = {"regulation", "source"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String regulation;

    @Column(nullable = false)
    private String source;

    private String label;

    private String description;

    // Each source's EFFECTIVE scraping settings, pushed by the same sync
    // as label/description - see V10__add_regulation_source_settings.sql
    // for why recheckAfterDays/lookbackDays being null is a real,
    // meaningful value, not "not synced yet".
    private Boolean enabled;

    @Column(name = "requests_per_second")
    private Double requestsPerSecond;

    @Column(name = "max_new_documents_per_run")
    private Integer maxNewDocumentsPerRun;

    @Column(name = "recheck_after_days")
    private Integer recheckAfterDays;

    @Column(name = "lookback_days")
    private Integer lookbackDays;

    @Column(name = "synced_at", nullable = false)
    private OffsetDateTime syncedAt;
}
