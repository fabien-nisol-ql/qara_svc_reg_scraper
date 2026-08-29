package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.ScrapedDocumentEntity;
import com.qaralink.regscraper.model.db.repo.ScrapedDocumentRepository;
import com.qaralink.regscraper.model.dto.ScrapedDocumentDTO;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class ScrapedDocumentService {

    private final ScrapedDocumentRepository repository;

    public ScrapedDocumentService(ScrapedDocumentRepository repository) {
        this.repository = repository;
    }

    /**
     * Upsert-by-(regulation, source, documentId) — the "record what
     * scraping just did" primitive qara_cli_reg_scraper's
     * {@code Manifest.save_document}/{@code record_event} will eventually
     * call once for every new/updated/unchanged document.
     */
    public ScrapedDocumentDTO upsert(ScrapedDocumentDTO dto) {
        ScrapedDocumentEntity entity = repository
                .findByRegulationAndSourceAndDocumentId(dto.getRegulation(), dto.getSource(), dto.getDocumentId())
                .orElseGet(() -> ScrapedDocumentEntity.builder()
                        .regulation(dto.getRegulation())
                        .source(dto.getSource())
                        .documentId(dto.getDocumentId())
                        .build());

        entity.setTitle(dto.getTitle());
        entity.setOriginalFilename(dto.getOriginalFilename());
        entity.setCanonicalUrl(dto.getCanonicalUrl());
        entity.setStoragePath(dto.getStoragePath());
        entity.setContentHash(dto.getContentHash());
        entity.setContentType(dto.getContentType());
        entity.setSizeBytes(dto.getSizeBytes());
        entity.setVersionCount(dto.getVersionCount() == null ? 1 : dto.getVersionCount());
        entity.setFirstSeenAt(dto.getFirstSeenAt());
        entity.setLastScrapedAt(dto.getLastScrapedAt());
        entity.setLastCheckedAt(dto.getLastCheckedAt());
        entity.setLastChangedAt(dto.getLastChangedAt());
        entity.setSourceMetadata(dto.getSourceMetadata());

        ScrapedDocumentEntity saved = entity.getId() == null ? repository.save(entity) : repository.update(entity);
        return ScrapedDocumentDTO.from(saved);
    }

    public Page<ScrapedDocumentDTO> search(String regulation, String source, Pageable pageable) {
        return search(regulation, source, null, pageable);
    }

    /**
     * Same as {@link #search(String, String, Pageable)}, plus an optional free-text filter
     * (matched against title/documentId/originalFilename) — see
     * {@link ScrapedDocumentRepository#search}. Used by the "browse/search indexed documents"
     * UI, where regulation/source/query are all independently optional.
     */
    public Page<ScrapedDocumentDTO> search(
            @Nullable String regulation, @Nullable String source, @Nullable String query, Pageable pageable) {
        Page<ScrapedDocumentEntity> page = repository.search(regulation, source, blankToNull(query), pageable);
        return page.map(ScrapedDocumentDTO::from);
    }

    public long count(String regulation, String source) {
        return repository.countByRegulationAndSource(regulation, source);
    }

    /** For GET /v1/documents/{id}(/content) — id is the row id, not documentId. */
    public Optional<ScrapedDocumentEntity> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<ScrapedDocumentDTO> get(Long id) {
        return findById(id).map(ScrapedDocumentDTO::from);
    }

    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
