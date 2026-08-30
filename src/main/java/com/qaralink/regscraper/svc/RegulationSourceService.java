package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.RegulationSourceEntity;
import com.qaralink.regscraper.model.db.repo.RegulationSourceRepository;
import com.qaralink.regscraper.model.dto.RegulationSourceDTO;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces the old hand-maintained {@code RegulationSourceRegistry} — the
 * table this reads/writes is kept current entirely by
 * qara_cli_reg_scraper itself, via {@link #replaceAll}. See that repo's
 * docs/source-registry-sync.md for exactly when a sync happens (service
 * startup, and once per real {@code run} invocation) — nothing here polls
 * or schedules a sync on its own.
 */
@Singleton
public class RegulationSourceService {

    private final RegulationSourceRepository repository;

    public RegulationSourceService(RegulationSourceRepository repository) {
        this.repository = repository;
    }

    public List<RegulationSourceDTO> all() {
        return repository.findAll().stream().map(RegulationSourceDTO::from).toList();
    }

    public List<RegulationSourceDTO> forRegulation(String regulation) {
        return repository.findAllByRegulation(regulation).stream().map(RegulationSourceDTO::from).toList();
    }

    /**
     * Full replace-in-place sync: upserts every entry in {@code incoming}
     * by (regulation, source), then deletes any existing row NOT present
     * in {@code incoming} — a source the CLI no longer knows about
     * disappears from GET /v1/sources too, not just additively grows.
     * {@code incoming} must be the CLI's ENTIRE known-source registry,
     * never a partial list — see RegulationSourceDTO's own docstring and
     * the CLI-side docs this mirrors.
     */
    @Transactional
    public List<RegulationSourceDTO> replaceAll(List<RegulationSourceDTO> incoming) {
        OffsetDateTime now = OffsetDateTime.now();
        Set<String> incomingKeys = incoming.stream()
                .map(dto -> key(dto.getRegulation(), dto.getSource()))
                .collect(Collectors.toSet());

        List<RegulationSourceEntity> stale = repository.findAll().stream()
                .filter(e -> !incomingKeys.contains(key(e.getRegulation(), e.getSource())))
                .toList();
        if (!stale.isEmpty()) {
            repository.deleteAll(stale);
        }

        List<RegulationSourceEntity> saved = incoming.stream().map(dto -> {
            RegulationSourceEntity entity = repository.findByRegulationAndSource(dto.getRegulation(), dto.getSource())
                    .orElseGet(() -> RegulationSourceEntity.builder()
                            .regulation(dto.getRegulation())
                            .source(dto.getSource())
                            .build());
            entity.setLabel(dto.getLabel());
            entity.setDescription(dto.getDescription());
            entity.setEnabled(dto.getEnabled());
            entity.setRequestsPerSecond(dto.getRequestsPerSecond());
            entity.setMaxNewDocumentsPerRun(dto.getMaxNewDocumentsPerRun());
            entity.setRecheckAfterDays(dto.getRecheckAfterDays());
            entity.setLookbackDays(dto.getLookbackDays());
            entity.setSyncedAt(now);
            return entity.getId() == null ? repository.save(entity) : repository.update(entity);
        }).toList();

        return saved.stream().map(RegulationSourceDTO::from).toList();
    }

    private static String key(String regulation, String source) {
        return regulation + ":" + source;
    }
}
