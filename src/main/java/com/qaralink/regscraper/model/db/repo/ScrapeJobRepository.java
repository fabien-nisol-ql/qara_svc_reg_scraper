package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.ScrapeJobEntity;
import com.qaralink.regscraper.model.dto.JobStatus;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import jakarta.annotation.Nullable;

import java.util.List;

@Repository
public interface ScrapeJobRepository extends PageableRepository<ScrapeJobEntity, String> {

    // Sorting is the caller's job via Pageable (e.g.
    // Pageable.from(0, 20, Sort.of(Sort.Order.desc("submittedAt")))) —
    // "findAllByOrderBy..." with no leading property isn't valid Micronaut
    // Data query-derivation syntax.

    List<ScrapeJobEntity> findAllByStatusIn(List<JobStatus> statuses);

    /**
     * Jobs whose {@code sources} includes the given "<regulation>:<source>" qualified name — a
     * job can be triggered for several sources at once, so this can't be a plain equality match.
     * {@code sources} is a JSON array column (see StringListConverter); matching against the
     * quoted, comma/bracket-delimited substring (`"fda:ecfr"`, quotes included) avoids a bare
     * LIKE '%fda:ecfr%' collateral-matching a longer name that merely starts with it (there's no
     * such name today, but nothing stops one existing later).
     * <p>
     * {@code source == null} returns every job, unfiltered - same {@code :param IS NULL OR ...}
     * pattern as ScrapedDocumentRepository.search, including that query's CAST(:source AS string)
     * fix for the same pgjdbc null-parameter-typed-as-bytea issue (confirmed to reproduce here
     * too before the cast was added).
     */
    @Query(value = """
            SELECT j FROM ScrapeJobEntity j
            WHERE :source IS NULL OR j.sources LIKE CONCAT('%"', CAST(:source AS string), '"%')
            """,
            countQuery = """
            SELECT COUNT(j) FROM ScrapeJobEntity j
            WHERE :source IS NULL OR j.sources LIKE CONCAT('%"', CAST(:source AS string), '"%')
            """)
    Page<ScrapeJobEntity> search(@Nullable String source, Pageable pageable);
}
