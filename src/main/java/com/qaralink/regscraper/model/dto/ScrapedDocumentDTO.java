package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.ScrapedDocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * A scraped document's current state — both the upsert request body qara-
 * reg-scraper-cli's {@code Manifest.save_document} will eventually POST,
 * and the read response for {@code GET /v1/documents}. Mirrors
 * qara_cli_reg_scraper's manifest {@code current.meta.json} sidecar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current state of one scraped document, one per (regulation, source, documentId).")
public class ScrapedDocumentDTO {

    @Schema(description = "Row id — stable and slash-free, unlike documentId (which can itself contain "
            + "'/', e.g. \"K252474/summary\"). Use this to address GET /v1/documents/{id}/content. "
            + "Absent/null on an upsert request body; always present on a read response.")
    private Long id;

    @Schema(description = "Regulation namespace, e.g. \"fda\".", example = "fda", required = true)
    private String regulation;

    @Schema(description = "Source name within the regulation, e.g. \"ecfr\".", example = "ecfr", required = true)
    private String source;

    @Schema(description = "Document id, unique within (regulation, source).", example = "part-800", required = true)
    private String documentId;

    private String title;
    private String originalFilename;
    private String canonicalUrl;
    private String storagePath;
    private String contentHash;
    private String contentType;
    private Long sizeBytes;
    private Integer versionCount;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastScrapedAt;
    private OffsetDateTime lastCheckedAt;
    private OffsetDateTime lastChangedAt;

    @Builder.Default
    private Map<String, Object> sourceMetadata = Map.of();

    public static ScrapedDocumentDTO from(ScrapedDocumentEntity e) {
        return ScrapedDocumentDTO.builder()
                .id(e.getId())
                .regulation(e.getRegulation())
                .source(e.getSource())
                .documentId(e.getDocumentId())
                .title(e.getTitle())
                .originalFilename(e.getOriginalFilename())
                .canonicalUrl(e.getCanonicalUrl())
                .storagePath(e.getStoragePath())
                .contentHash(e.getContentHash())
                .contentType(e.getContentType())
                .sizeBytes(e.getSizeBytes())
                .versionCount(e.getVersionCount())
                .firstSeenAt(e.getFirstSeenAt())
                .lastScrapedAt(e.getLastScrapedAt())
                .lastCheckedAt(e.getLastCheckedAt())
                .lastChangedAt(e.getLastChangedAt())
                .sourceMetadata(e.getSourceMetadata())
                .build();
    }
}
