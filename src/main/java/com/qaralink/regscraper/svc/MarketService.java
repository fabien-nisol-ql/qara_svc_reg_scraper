package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.MarketEntity;
import com.qaralink.regscraper.model.db.MarketPathEntity;
import com.qaralink.regscraper.model.db.repo.MarketPathRepository;
import com.qaralink.regscraper.model.db.repo.MarketRepository;
import com.qaralink.regscraper.model.dto.MarketDTO;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * See {@code MarketEntity}'s own docstring for why this service (and not
 * {@code QARA_SVC_CMPL}, where this feature originally lived) is now the
 * source of truth for the admin UI's markets page.
 */
@Singleton
public class MarketService {

    private final MarketRepository marketRepository;
    private final MarketPathRepository marketPathRepository;

    public MarketService(MarketRepository marketRepository, MarketPathRepository marketPathRepository) {
        this.marketRepository = marketRepository;
        this.marketPathRepository = marketPathRepository;
    }

    public Page<MarketDTO> list(Pageable pageable) {
        Pageable p = pageable == null ? Pageable.UNPAGED : pageable;
        return marketRepository.findAll(p).map(this::toDto);
    }

    public Optional<MarketDTO> findByCode(String code) {
        return marketRepository.findById(code).map(this::toDto);
    }

    private MarketDTO toDto(MarketEntity market) {
        List<MarketPathEntity> paths = marketPathRepository.findAllByMarketCode(market.getCode());
        return MarketDTO.from(market, paths);
    }
}
