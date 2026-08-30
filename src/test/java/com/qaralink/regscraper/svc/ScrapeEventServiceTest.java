package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.ScrapeEventEntity;
import com.qaralink.regscraper.model.db.repo.ScrapeEventRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers a real, live-caught gap (2026-08-30): GET /v1/events with
 * `regulation`/`source` but no `runId` used to silently ignore `event`
 * entirely - a `?event=error` query returned every event type, not just
 * errors. That query shape is exactly what a source card needs to poll
 * its own most-recent error while a run is still in progress (no single
 * stable runId to scope by yet, unlike the already-working
 * byRun+event path).
 */
@ExtendWith(MockitoExtension.class)
class ScrapeEventServiceTest {

    @Mock
    ScrapeEventRepository repository;

    ScrapeEventService service;

    @BeforeEach
    void setUp() {
        service = new ScrapeEventService(repository);
    }

    @Test
    void searchWithAnEventFilterUsesTheFilteredRepositoryMethod() {
        ScrapeEventEntity errorEvent = ScrapeEventEntity.builder()
                .regulation("ca").source("guidance").documentId("www.mdsap.global").event("error")
                .build();
        when(repository.findAllByRegulationAndSourceAndEvent("ca", "guidance", "error", Pageable.UNPAGED))
                .thenReturn(Page.of(List.of(errorEvent), Pageable.UNPAGED, 1L));

        Page<?> result = service.search("ca", "guidance", "error", Pageable.UNPAGED);

        assertEquals(1, result.getContent().size());
        verify(repository, never()).findAllByRegulationAndSource("ca", "guidance", Pageable.UNPAGED);
    }

    @Test
    void searchWithNoEventFilterFallsBackToTheUnfilteredRepositoryMethod() {
        when(repository.findAllByRegulationAndSource("ca", "guidance", Pageable.UNPAGED))
                .thenReturn(Page.of(List.of(), Pageable.UNPAGED, 0L));

        service.search("ca", "guidance", null, Pageable.UNPAGED);

        verify(repository, never()).findAllByRegulationAndSourceAndEvent(any(), any(), any(), any());
    }
}
