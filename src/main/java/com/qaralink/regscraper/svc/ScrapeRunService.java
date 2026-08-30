package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.ScrapeRunEntity;
import com.qaralink.regscraper.model.db.repo.ScrapeRunRepository;
import com.qaralink.regscraper.model.dto.ScrapeRunDTO;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class ScrapeRunService {

    private final ScrapeRunRepository repository;

    public ScrapeRunService(ScrapeRunRepository repository) {
        this.repository = repository;
    }

    /**
     * Upsert-by-runId — the CLI POSTs this once when a run starts
     * (status "running") and again when {@code Manifest.finalize()} runs,
     * with the final counts/status.
     */
    public ScrapeRunDTO upsert(ScrapeRunDTO dto) {
        boolean exists = repository.existsById(dto.getRunId());
        ScrapeRunEntity entity = ScrapeRunEntity.builder()
                .runId(dto.getRunId())
                .regulation(dto.getRegulation())
                .source(dto.getSource())
                .startedAt(dto.getStartedAt())
                .finishedAt(dto.getFinishedAt())
                .status(dto.getStatus())
                .checked(dto.getChecked())
                .newCount(dto.getNew_())
                .updated(dto.getUpdated())
                .unchanged(dto.getUnchanged())
                .errors(dto.getErrors())
                .errorDetails(dto.getErrorDetails())
                .stopReason(dto.getStopReason())
                .build();
        ScrapeRunEntity saved = exists ? repository.update(entity) : repository.save(entity);
        return ScrapeRunDTO.from(saved);
    }

    public Page<ScrapeRunDTO> search(String regulation, String source, Pageable pageable) {
        Page<ScrapeRunEntity> page = repository.findAllByRegulationAndSource(regulation, source, pageable);
        return page.map(ScrapeRunDTO::from);
    }

    public Optional<ScrapeRunDTO> latest(String regulation, String source) {
        return repository.findFirstByRegulationAndSourceOrderByStartedAtDesc(regulation, source)
                .map(ScrapeRunDTO::from);
    }
}
