package com.qaralink.regscraper.model.db;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One row per manifest event file — the full history, including every
 * unchanged-check, not just changes. Mirrors qara_cli_reg_scraper's
 * {@code ScrapeEvent} SQLAlchemy model.
 */
@Entity
@Table(name = "scrape_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id")
    private String runId;

    @Column(nullable = false)
    private String regulation;

    @Column(nullable = false)
    private String source;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(nullable = false)
    private String event;

    private OffsetDateTime ts;

    private String url;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(length = 4096)
    private String error;
}
