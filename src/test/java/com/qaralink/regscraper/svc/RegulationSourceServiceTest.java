package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.RegulationSourceEntity;
import com.qaralink.regscraper.model.db.repo.RegulationSourceRepository;
import com.qaralink.regscraper.model.dto.RegulationSourceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers replaceAll's own effective-settings fields, added 2026-08-30 -
 * previously only label/description were synced from the CLI; enabled/
 * requestsPerSecond/maxNewDocumentsPerRun/recheckAfterDays/lookbackDays
 * are what actually surfaces a source's real scraping behavior (limits,
 * cadence, pacing) to an end user via GET /v1/sources.
 */
@ExtendWith(MockitoExtension.class)
class RegulationSourceServiceTest {

    @Mock
    RegulationSourceRepository repository;

    RegulationSourceService service;

    @BeforeEach
    void setUp() {
        service = new RegulationSourceService(repository);
    }

    @Test
    void replaceAllCarriesEffectiveSettingsThroughToANewRow() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findByRegulationAndSource("fda", "ecfr")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegulationSourceDTO incoming = RegulationSourceDTO.builder()
                .regulation("fda").source("ecfr").label("eCFR").description("21 CFR Title 21")
                .enabled(true).requestsPerSecond(1.0).maxNewDocumentsPerRun(1000)
                .recheckAfterDays(14).lookbackDays(null)
                .build();

        List<RegulationSourceDTO> result = service.replaceAll(List.of(incoming));

        RegulationSourceDTO saved = result.get(0);
        assertEquals(true, saved.getEnabled());
        assertEquals(1.0, saved.getRequestsPerSecond());
        assertEquals(1000, saved.getMaxNewDocumentsPerRun());
        assertEquals(14, saved.getRecheckAfterDays());
        assertNull(saved.getLookbackDays());
    }

    @Test
    void replaceAllOverwritesAnExistingRowsSettingsOnEverySync() {
        // A stale value from a previous sync must not survive if the CLI's
        // own config.yaml changed since - a fresh sync always wins, same
        // as label/description already do.
        RegulationSourceEntity existing = RegulationSourceEntity.builder()
                .id(1L).regulation("fda").source("ecfr")
                .maxNewDocumentsPerRun(25).recheckAfterDays(90)
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.findByRegulationAndSource("fda", "ecfr")).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegulationSourceDTO incoming = RegulationSourceDTO.builder()
                .regulation("fda").source("ecfr")
                .maxNewDocumentsPerRun(1000).recheckAfterDays(14)
                .build();

        List<RegulationSourceDTO> result = service.replaceAll(List.of(incoming));

        assertEquals(1000, result.get(0).getMaxNewDocumentsPerRun());
        assertEquals(14, result.get(0).getRecheckAfterDays());
    }
}
