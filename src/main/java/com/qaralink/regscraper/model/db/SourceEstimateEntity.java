package com.qaralink.regscraper.model.db;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Latest known "what's left to do" snapshot for one (regulation, source)
 * pair — mirrors qara_cli_reg_scraper's {@code SourceEstimate} SQLAlchemy
 * model and the manifest's {@code estimate.json} file 1:1. Unlike
 * {@link ScrapeRunEntity}/{@link ScrapeEventEntity} this is *not*
 * historical: every real run (and {@code reindex}) overwrites it in place.
 */
@Entity
@Table(name = "source_estimate", uniqueConstraints = @UniqueConstraint(
        name = "uq_regulation_source_estimate", columnNames = {"regulation", "source"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceEstimateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String regulation;

    @Column(nullable = false)
    private String source;

    @Column(name = "computed_at")
    private OffsetDateTime computedAt;

    @Column(name = "total_available")
    private Integer totalAvailable;

    @Column(name = "already_known")
    private Integer alreadyKnown;

    private Integer remaining;

    private String note;
}
