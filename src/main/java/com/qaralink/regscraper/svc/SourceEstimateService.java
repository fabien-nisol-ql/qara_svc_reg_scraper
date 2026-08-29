package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.SourceEstimateEntity;
import com.qaralink.regscraper.model.db.repo.SourceEstimateRepository;
import com.qaralink.regscraper.model.dto.SourceEstimateDTO;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class SourceEstimateService {

    private final SourceEstimateRepository repository;

    public SourceEstimateService(SourceEstimateRepository repository) {
        this.repository = repository;
    }

    /**
     * Replace-in-place upsert for one (regulation, source) — mirrors
     * {@code Manifest.write_estimate}'s "overwrite, not historical"
     * semantics. Called right after a real run finishes, and by
     * {@code reindex} rebuilding from the manifest's estimate.json file.
     */
    @Transactional
    public SourceEstimateDTO upsert(SourceEstimateDTO dto) {
        SourceEstimateEntity entity = repository.findByRegulationAndSource(dto.getRegulation(), dto.getSource())
                .orElseGet(() -> SourceEstimateEntity.builder()
                        .regulation(dto.getRegulation())
                        .source(dto.getSource())
                        .build());
        entity.setComputedAt(dto.getComputedAt());
        entity.setTotalAvailable(dto.getTotalAvailable());
        entity.setAlreadyKnown(dto.getAlreadyKnown());
        entity.setRemaining(dto.getRemaining());
        entity.setNote(dto.getNote());
        SourceEstimateEntity saved = entity.getId() == null ? repository.save(entity) : repository.update(entity);
        return SourceEstimateDTO.from(saved);
    }

    public Optional<SourceEstimateDTO> find(String regulation, String source) {
        return repository.findByRegulationAndSource(regulation, source).map(SourceEstimateDTO::from);
    }
}
