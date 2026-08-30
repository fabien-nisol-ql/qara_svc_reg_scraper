package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.ScrapeEventEntity;
import com.qaralink.regscraper.model.db.repo.ScrapeEventRepository;
import com.qaralink.regscraper.model.dto.ScrapeEventDTO;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

@Singleton
public class ScrapeEventService {

    private final ScrapeEventRepository repository;

    public ScrapeEventService(ScrapeEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Always an insert, never an upsert — every event (including repeated
     * "unchanged" checks) is its own row, mirroring qara_cli_reg_scraper's
     * one-file-per-event manifest design.
     */
    public ScrapeEventDTO record(ScrapeEventDTO dto) {
        ScrapeEventEntity entity = ScrapeEventEntity.builder()
                .runId(dto.getRunId())
                .regulation(dto.getRegulation())
                .source(dto.getSource())
                .documentId(dto.getDocumentId())
                .event(dto.getEvent())
                .ts(dto.getTs())
                .url(dto.getUrl())
                .httpStatus(dto.getHttpStatus())
                .contentHash(dto.getContentHash())
                .storagePath(dto.getStoragePath())
                .error(dto.getError())
                .build();
        return ScrapeEventDTO.from(repository.save(entity));
    }

    public Page<ScrapeEventDTO> search(String regulation, String source, String event, Pageable pageable) {
        if (event != null) {
            return repository.findAllByRegulationAndSourceAndEvent(regulation, source, event, pageable)
                    .map(ScrapeEventDTO::from);
        }
        return repository.findAllByRegulationAndSource(regulation, source, pageable).map(ScrapeEventDTO::from);
    }

    public Page<ScrapeEventDTO> byRun(String runId, String event, Pageable pageable) {
        if (event != null) {
            return repository.findAllByRunIdAndEvent(runId, event, pageable).map(ScrapeEventDTO::from);
        }
        return repository.findAllByRunId(runId, pageable).map(ScrapeEventDTO::from);
    }
}
