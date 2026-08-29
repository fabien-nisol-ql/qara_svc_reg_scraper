package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.ScrapeJobHistoryEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;

@Repository
public interface ScrapeJobHistoryRepository extends PageableRepository<ScrapeJobHistoryEntity, Long> {

    Page<ScrapeJobHistoryEntity> findAllByJobIdOrderByChangedAtDesc(String jobId, Pageable pageable);
}
