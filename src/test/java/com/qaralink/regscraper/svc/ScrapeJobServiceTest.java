package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.exceptions.BotBlockCooldownActiveException;
import com.qaralink.regscraper.model.api.TriggerScrapeJobRequest;
import com.qaralink.regscraper.model.db.SourceRetryStateEntity;
import com.qaralink.regscraper.model.db.repo.ScrapeJobHistoryRepository;
import com.qaralink.regscraper.model.db.repo.ScrapeJobRepository;
import com.qaralink.regscraper.svc.workload.WorkloadOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the one new behavior added 2026-08-30: a manual retry (POST
 * /v1/jobs/scrape) is REFUSED, not silently allowed, for a source suspended
 * due to a detected bot-management block before
 * qaralink.scheduler.bot-block-cooldown-hours has elapsed — see
 * BotBlockCooldownActiveException's own javadoc for why this has to be
 * server-side, not just a disabled button.
 */
@ExtendWith(MockitoExtension.class)
class ScrapeJobServiceTest {

    @Mock
    ScrapeJobRepository jobRepository;
    @Mock
    ScrapeJobHistoryRepository historyRepository;
    @Mock
    WorkloadOrchestrator orchestrator;
    @Mock
    SourceRetryStateService retryStateService;

    ScrapeJobService service;

    @BeforeEach
    void setUp() {
        service = new ScrapeJobService(
                jobRepository, historyRepository, orchestrator, "docker", retryStateService, 24
        );
    }

    @Test
    void aManualRetryIsRefusedDuringAnActiveBotBlockCooldown() {
        when(retryStateService.find("fda", "clearances_510k")).thenReturn(Optional.of(
                SourceRetryStateEntity.builder()
                        .regulation("fda").source("clearances_510k")
                        .suspended(true)
                        .suspendedDueToBotBlock(true)
                        .suspendedAt(OffsetDateTime.now().minusHours(1)) // only 1 of 24h elapsed
                        .build()
        ));
        TriggerScrapeJobRequest request = TriggerScrapeJobRequest.builder()
                .sources(List.of("fda:clearances_510k"))
                .build();

        assertThrows(BotBlockCooldownActiveException.class, () -> service.trigger(request, "manual"));

        // Fails BEFORE doing any real work - never resets the circuit breaker, never submits a job.
        verify(retryStateService, never()).resetToHealthy(any(), any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void anExplicitOverrideBypassesAnActiveBotBlockCooldown() {
        // retryStateService.find is never even called with the override set - the whole
        // cooldown check (including looking up the retry-state row) is skipped entirely, not
        // just its throw - see trigger()'s own `if (!override)` gate.
        TriggerScrapeJobRequest request = TriggerScrapeJobRequest.builder()
                .sources(List.of("fda:clearances_510k"))
                .overrideBotBlockCooldown(true)
                .build();

        // Real orchestration (unstubbed here) is caught internally by trigger()'s own try/catch
        // and turned into a failed job, not propagated - so completing without throwing IS the
        // signal under test: the cooldown check was bypassed, not that anything downstream works.
        assertNotNull(service.trigger(request, "manual"));

        verify(retryStateService).resetToHealthy("fda", "clearances_510k");
    }

    @Test
    void aManualRetryStillWorksAfterTheCooldownElapses() {
        when(retryStateService.find("fda", "clearances_510k")).thenReturn(Optional.of(
                SourceRetryStateEntity.builder()
                        .regulation("fda").source("clearances_510k")
                        .suspended(true)
                        .suspendedDueToBotBlock(true)
                        .suspendedAt(OffsetDateTime.now().minusHours(25)) // past the 24h cooldown
                        .build()
        ));
        TriggerScrapeJobRequest request = TriggerScrapeJobRequest.builder()
                .sources(List.of("fda:clearances_510k"))
                .build();

        // Real orchestration (WorkloadOrchestrator.submitWorkloadAndProcess, unstubbed here) is
        // caught internally by trigger()'s own try/catch and turned into a failed job, not
        // propagated - so this completing without throwing, past the cooldown check, IS the
        // signal under test here.
        assertNotNull(service.trigger(request, "manual"));

        verify(retryStateService).resetToHealthy("fda", "clearances_510k");
    }

    @Test
    void aGenericNonBotBlockSuspensionHasNoCooldownAtAll() {
        when(retryStateService.find("fda", "clearances_510k")).thenReturn(Optional.of(
                SourceRetryStateEntity.builder()
                        .regulation("fda").source("clearances_510k")
                        .suspended(true)
                        .suspendedDueToBotBlock(false) // the generic consecutive-failures kind
                        .suspendedAt(OffsetDateTime.now()) // just now - would fail every cooldown check if applied
                        .build()
        ));
        TriggerScrapeJobRequest request = TriggerScrapeJobRequest.builder()
                .sources(List.of("fda:clearances_510k"))
                .build();

        // Not a bot-block suspension, so no cooldown applies - proceeds straight through to
        // resetToHealthy/orchestration, same as before this feature existed.
        assertNotNull(service.trigger(request, "manual"));

        verify(retryStateService).resetToHealthy("fda", "clearances_510k");
    }
}
