package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.SourceRetryStateEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceRetryStateRepository extends PageableRepository<SourceRetryStateEntity, Long> {

    Optional<SourceRetryStateEntity> findByRegulationAndSource(String regulation, String source);

    List<SourceRetryStateEntity> findAllByRegulation(String regulation);
}
