package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.ScrapeRunEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@Repository
public interface ScrapeRunRepository extends PageableRepository<ScrapeRunEntity, String> {

    Page<ScrapeRunEntity> findAllByRegulationAndSource(String regulation, String source, Pageable pageable);

    Optional<ScrapeRunEntity> findFirstByRegulationAndSourceOrderByStartedAtDesc(String regulation, String source);
}
