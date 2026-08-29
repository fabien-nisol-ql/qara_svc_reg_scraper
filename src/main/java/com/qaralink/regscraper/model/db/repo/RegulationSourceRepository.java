package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.RegulationSourceEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegulationSourceRepository extends PageableRepository<RegulationSourceEntity, Long> {

    Optional<RegulationSourceEntity> findByRegulationAndSource(String regulation, String source);

    List<RegulationSourceEntity> findAllByRegulation(String regulation);

    // findAll()/deleteAll(Iterable) come from PageableRepository/CrudRepository -
    // RegulationSourceService#replaceAll uses those directly for the
    // delete-stale-rows step, rather than a composite-key "NOT IN a list of
    // pairs" derived query, which Micronaut Data can't express cleanly.
}
