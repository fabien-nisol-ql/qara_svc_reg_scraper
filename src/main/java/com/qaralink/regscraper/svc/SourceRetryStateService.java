package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.SourceRetryStateEntity;
import com.qaralink.regscraper.model.db.repo.SourceRetryStateRepository;
import com.qaralink.regscraper.model.dto.RegulationSourceDTO;
import com.qaralink.regscraper.model.dto.SourceRetryStateDTO;
import jakarta.inject.Singleton;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence primitives for the {@code source_retry_state} table — the
 * decision logic (when to retry, when to suspend) lives in
 * {@link com.qaralink.regscraper.scheduler.SourceRetryScheduler}, which
 * calls these; this service is deliberately thin, unlike e.g.
 * {@link SourceEstimateService} which owns real upsert semantics of its
 * own, because retry state is scheduler-owned end to end — nothing else
 * ever writes it (contrast {@link RegulationSourceService}/{@link SourceEstimateService},
 * both written by the CLI over REST).
 */
@Singleton
public class SourceRetryStateService {

    private final SourceRetryStateRepository repository;

    public SourceRetryStateService(SourceRetryStateRepository repository) {
        this.repository = repository;
    }

    public Optional<SourceRetryStateEntity> find(String regulation, String source) {
        return repository.findByRegulationAndSource(regulation, source);
    }

    /** Not persisted — caller (the scheduler) sets whatever fields it needs, then {@link #save}. */
    public SourceRetryStateEntity findOrNew(String regulation, String source) {
        return find(regulation, source).orElseGet(() -> SourceRetryStateEntity.builder()
                .regulation(regulation)
                .source(source)
                .build());
    }

    public SourceRetryStateEntity save(SourceRetryStateEntity entity) {
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity.getId() == null ? repository.save(entity) : repository.update(entity);
    }

    /** A source that's fully caught up and healthy again — clears the circuit
     * breaker entirely. Also called when a human manually re-triggers a
     * suspended source (see ScrapeJobController#trigger), so fixing the
     * underlying issue and clicking "Update now" un-sticks it rather than
     * leaving it suspended forever even after it's fixed. No-op if there's
     * no row at all (never unhealthy in the first place). */
    public void resetToHealthy(String regulation, String source) {
        find(regulation, source).ifPresent(entity -> {
            entity.setConsecutiveFailures(0);
            entity.setSuspended(false);
            entity.setSuspendedReason(null);
            entity.setNextRetryAt(null);
            save(entity);
        });
    }

    /** Every known source's retry state, defaulting a source with no row
     * yet (never evaluated) rather than omitting it — see
     * SourceRetryStateDTO#defaultFor. */
    public List<SourceRetryStateDTO> allFor(List<RegulationSourceDTO> knownSources) {
        return knownSources.stream()
                .map(known -> find(known.getRegulation(), known.getSource())
                        .map(SourceRetryStateDTO::from)
                        .orElseGet(() -> SourceRetryStateDTO.defaultFor(known.getRegulation(), known.getSource())))
                .toList();
    }
}
