package com.qaralink.regscraper.scheduler;

import com.qaralink.regscraper.model.db.SourceRetryStateEntity;
import com.qaralink.regscraper.model.db.repo.ScrapeJobRepository;
import com.qaralink.regscraper.model.db.repo.SourceEstimateRepository;
import com.qaralink.regscraper.model.dto.RegulationSourceDTO;
import com.qaralink.regscraper.model.dto.ScrapeRunDTO;
import com.qaralink.regscraper.svc.RegulationSourceService;
import com.qaralink.regscraper.svc.ScrapeJobService;
import com.qaralink.regscraper.svc.ScrapeRunService;
import com.qaralink.regscraper.svc.SourceRetryStateService;
import io.micronaut.data.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the one new behavior added 2026-08-30: a detected bot-management
 * block suspends automatic retry immediately, before the generic
 * consecutive-failures threshold is ever consulted — see the class's own
 * updated javadoc for the full "why" (continuing to automatically retry
 * against a host mid-block plausibly extends its own reputation/volume-
 * based cooldown rather than ever letting it clear, confirmed live against
 * accessdata.fda.gov). No other test file exists for this scheduler yet;
 * this one is deliberately scoped to just the new behavior rather than
 * re-covering the whole pre-existing state machine.
 */
@ExtendWith(MockitoExtension.class)
class SourceRetrySchedulerTest {

    @Mock
    RegulationSourceService sourceService;
    @Mock
    SourceRetryStateService retryStateService;
    @Mock
    SourceEstimateRepository estimateRepository;
    @Mock
    ScrapeJobRepository jobRepository;
    @Mock
    ScrapeJobService jobService;
    @Mock
    ScrapeRunService runService;

    SourceRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SourceRetryScheduler(
                sourceService, retryStateService, estimateRepository, jobRepository, jobService, runService,
                60, 1440, 5
        );
        when(sourceService.all()).thenReturn(List.of(
                RegulationSourceDTO.builder().regulation("fda").source("clearances_510k").build()
        ));
        when(retryStateService.findOrNew("fda", "clearances_510k"))
                .thenReturn(SourceRetryStateEntity.builder().regulation("fda").source("clearances_510k").build());
    }

    @Test
    void aBotBlockSuspendsImmediatelyWithoutConsultingConsecutiveFailures() {
        when(runService.latest("fda", "clearances_510k"))
                .thenReturn(Optional.of(ScrapeRunDTO.builder().stopReason("bot_block").build()));

        scheduler.tick();

        ArgumentCaptor<SourceRetryStateEntity> saved = ArgumentCaptor.forClass(SourceRetryStateEntity.class);
        verify(retryStateService).save(saved.capture());
        SourceRetryStateEntity state = saved.getValue();
        assertTrue(state.getSuspended());
        assertNotNull(state.getSuspendedAt());
        assertTrue(state.getSuspendedDueToBotBlock());
        assertTrue(state.getSuspendedReason().contains("bot-management block"));
        // Never even looked at job history/estimate to decide this - the whole point.
        verify(jobRepository, never()).search(any(), any());
        verify(jobService, never()).triggerRetry(anyString(), anyInt());
    }

    @Test
    void aPlainHardStopDoesNotSuspendOnTheFirstFailure() {
        when(runService.latest("fda", "clearances_510k")).thenReturn(
                Optional.of(ScrapeRunDTO.builder().stopReason("hard_stop").build())
        );
        when(jobRepository.search(eq("fda:clearances_510k"), any()))
                .thenReturn(Page.of(List.of(), io.micronaut.data.model.Pageable.from(0, 1), 0L));
        when(estimateRepository.findByRegulationAndSource("fda", "clearances_510k")).thenReturn(Optional.empty());

        scheduler.tick();

        ArgumentCaptor<SourceRetryStateEntity> saved = ArgumentCaptor.forClass(SourceRetryStateEntity.class);
        verify(retryStateService).save(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getSuspended());
    }

    @Test
    void anAlreadySuspendedSourceIsSkippedEntirelyRegardlessOfLatestRun() {
        when(retryStateService.findOrNew("fda", "clearances_510k")).thenReturn(
                SourceRetryStateEntity.builder().regulation("fda").source("clearances_510k")
                        .suspended(true).build()
        );

        scheduler.tick();

        verify(runService, never()).latest(any(), any());
        verify(retryStateService, never()).save(any());
    }
}
