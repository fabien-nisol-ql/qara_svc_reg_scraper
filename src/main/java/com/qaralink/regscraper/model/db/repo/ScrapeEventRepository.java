package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.ScrapeEventEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;

@Repository
public interface ScrapeEventRepository extends PageableRepository<ScrapeEventEntity, Long> {

    Page<ScrapeEventEntity> findAllByRegulationAndSource(String regulation, String source, Pageable pageable);

    Page<ScrapeEventEntity> findAllByRunId(String runId, Pageable pageable);

    // Lets callers ask for just the "error" rows of one run without paging through every
    // "unchanged"/"new" event too — a run can revisit hundreds of already-known documents
    // (see max_new_documents_per_run) that generate zero error rows but would otherwise
    // dominate the page.
    Page<ScrapeEventEntity> findAllByRunIdAndEvent(String runId, String event, Pageable pageable);
}
