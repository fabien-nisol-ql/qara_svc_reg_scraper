package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.ScrapedDocumentEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import jakarta.annotation.Nullable;

import java.util.Optional;

@Repository
public interface ScrapedDocumentRepository extends PageableRepository<ScrapedDocumentEntity, Long> {

    Optional<ScrapedDocumentEntity> findByRegulationAndSourceAndDocumentId(
            String regulation, String source, String documentId);

    Page<ScrapedDocumentEntity> findAllByRegulationAndSource(String regulation, String source, Pageable pageable);

    Page<ScrapedDocumentEntity> findAllByRegulation(String regulation, Pageable pageable);

    long countByRegulationAndSource(String regulation, String source);

    /**
     * Free-text search across title/documentId/originalFilename, with regulation/source as
     * optional exact-match filters — every parameter is nullable and a null one is simply not
     * applied (the {@code :param IS NULL OR ...} pattern), so this single query backs every
     * combination DocumentController.search needs instead of one derived-name method per
     * combination of "regulation given?" x "source given?" x "query given?".
     * <p>
     * {@code CAST(:query AS string)} is load-bearing, not decorative: a bare {@code LOWER(:query)}
     * gave pgjdbc nothing to infer the bind parameter's type from when {@code :query} is null (no
     * mapped entity attribute anchors it, unlike {@code d.regulation = :regulation}'s equality
     * check) — confirmed live, it silently typed the parameter as {@code bytea} and every request
     * without a search term 500'd with "function lower(bytea) does not exist". regulation/source
     * don't need this: {@code IS NULL} and plain {@code =} against a mapped column both type fine.
     */
    @Query(value = """
            SELECT d FROM ScrapedDocumentEntity d
            WHERE (:regulation IS NULL OR d.regulation = :regulation)
              AND (:source IS NULL OR d.source = :source)
              AND (:query IS NULL
                   OR LOWER(d.title) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%')
                   OR LOWER(d.documentId) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%')
                   OR LOWER(d.originalFilename) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%'))
            """,
            countQuery = """
            SELECT COUNT(d) FROM ScrapedDocumentEntity d
            WHERE (:regulation IS NULL OR d.regulation = :regulation)
              AND (:source IS NULL OR d.source = :source)
              AND (:query IS NULL
                   OR LOWER(d.title) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%')
                   OR LOWER(d.documentId) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%')
                   OR LOWER(d.originalFilename) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%'))
            """)
    Page<ScrapedDocumentEntity> search(
            @Nullable String regulation, @Nullable String source, @Nullable String query, Pageable pageable);
}
