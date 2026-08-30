package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.MarketEntity;
import com.qaralink.regscraper.model.db.MarketPathEntity;
import com.qaralink.regscraper.model.db.repo.MarketPathRepository;
import com.qaralink.regscraper.model.db.repo.MarketRepository;
import com.qaralink.regscraper.model.dto.MarketDTO;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Market management, moved here from QARA_SVC_CMPL (2026-08-30) - see
 * MarketEntity's own docstring. Covers the one piece that's genuinely
 * this service's own design decision (not just a copy of CMPL's): a
 * market's paths are assembled via a second query rather than a JPA
 * @OneToMany relationship, matching every other entity in this codebase.
 */
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock
    MarketRepository marketRepository;
    @Mock
    MarketPathRepository marketPathRepository;

    MarketService service;

    @BeforeEach
    void setUp() {
        service = new MarketService(marketRepository, marketPathRepository);
    }

    @Test
    void findByCodeAssemblesTheMarketWithItsOwnPaths() {
        MarketEntity fda = MarketEntity.builder()
                .code("FDA").name("United States (FDA)").description("...").build();
        MarketPathEntity path510k = MarketPathEntity.builder()
                .code("510K").marketCode("FDA").name("510(k) Premarket Notification").description("...").build();
        when(marketRepository.findById("FDA")).thenReturn(Optional.of(fda));
        when(marketPathRepository.findAllByMarketCode("FDA")).thenReturn(List.of(path510k));

        Optional<MarketDTO> result = service.findByCode("FDA");

        assertTrue(result.isPresent());
        assertEquals("FDA", result.get().getCode());
        assertEquals(1, result.get().getPaths().size());
        assertEquals("510K", result.get().getPaths().get(0).getCode());
    }

    @Test
    void findByCodeReturnsEmptyForAnUnknownMarket() {
        when(marketRepository.findById("XX")).thenReturn(Optional.empty());

        assertTrue(service.findByCode("XX").isEmpty());
    }

    @Test
    void aMarketWithNoPathsYetGetsAnEmptyListNotAnError() {
        // Exactly CA's own state right now (see V11__add_market.sql) - no regulatory
        // pathway taxonomy has been decided for it yet.
        MarketEntity ca = MarketEntity.builder()
                .code("CA").name("Canada (Health Canada)").description("...").build();
        when(marketRepository.findById("CA")).thenReturn(Optional.of(ca));
        when(marketPathRepository.findAllByMarketCode("CA")).thenReturn(List.of());

        Optional<MarketDTO> result = service.findByCode("CA");

        assertTrue(result.isPresent());
        assertTrue(result.get().getPaths().isEmpty());
    }

    @Test
    void listReturnsEveryMarketWithItsOwnPaths() {
        MarketEntity fda = MarketEntity.builder().code("FDA").name("United States (FDA)").description("...").build();
        MarketEntity eu = MarketEntity.builder().code("EU").name("Europe (EU)").description("...").build();
        when(marketRepository.findAll(Pageable.UNPAGED))
                .thenReturn(Page.of(List.of(fda, eu), Pageable.UNPAGED, 2L));
        when(marketPathRepository.findAllByMarketCode("FDA")).thenReturn(List.of());
        when(marketPathRepository.findAllByMarketCode("EU")).thenReturn(List.of());

        Page<MarketDTO> result = service.list(null);

        assertEquals(2, result.getContent().size());
    }
}
