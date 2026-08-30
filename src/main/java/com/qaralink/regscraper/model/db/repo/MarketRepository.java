package com.qaralink.regscraper.model.db.repo;

import com.qaralink.regscraper.model.db.MarketEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.PageableRepository;

@Repository
public interface MarketRepository extends PageableRepository<MarketEntity, String> {
}
