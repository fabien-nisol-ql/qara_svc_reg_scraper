package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.SourceEstimateEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceEstimateRepository extends PageableRepository<SourceEstimateEntity, Long> {

    Optional<SourceEstimateEntity> findByRegulationAndSource(String regulation, String source);

    List<SourceEstimateEntity> findAllByRegulation(String regulation);

    void deleteByRegulationAndSource(String regulation, String source);
}
