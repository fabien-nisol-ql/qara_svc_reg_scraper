package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.MarketPathEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Repository
public interface MarketPathRepository extends CrudRepository<MarketPathEntity, String> {

    List<MarketPathEntity> findAllByMarketCode(String marketCode);
}
