package com.qaralink.regscraper.model.db;

import com.qaralink.regscraper.model.db.converters.SourceMetadataConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Current state of one document, one row per (regulation, source,
 * document_id) — mirrors qara_cli_reg_scraper's {@code ScrapedDocument}
 * SQLAlchemy model and, one level further back, a manifest
 * {@code current.meta.json} sidecar.
 */
@Entity
@Table(name = "scraped_document", uniqueConstraints = @UniqueConstraint(
        name = "uq_regulation_source_document", columnNames = {"regulation", "source", "document_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapedDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String regulation;

    @Column(nullable = false)
    private String source;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    private String title;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "canonical_url")
    private String canonicalUrl;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "version_count", nullable = false)
    @Builder.Default
    private Integer versionCount = 1;

    @Column(name = "first_seen_at")
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_scraped_at")
    private OffsetDateTime lastScrapedAt;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Column(name = "last_changed_at")
    private OffsetDateTime lastChangedAt;

    @Convert(converter = SourceMetadataConverter.class)
    @Column(name = "source_metadata", nullable = false)
    @Builder.Default
    private Map<String, Object> sourceMetadata = Map.of();
}
