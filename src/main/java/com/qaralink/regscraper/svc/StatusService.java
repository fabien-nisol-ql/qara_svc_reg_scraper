package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.model.db.repo.ScrapeRunRepository;
import com.qaralink.regscraper.model.db.repo.ScrapedDocumentRepository;
import com.qaralink.regscraper.model.db.repo.SourceEstimateRepository;
import com.qaralink.regscraper.model.dto.SourceStatusDTO;
import jakarta.inject.Singleton;

/**
 * One aggregated row per source — documents count + latest run + latest
 * estimate — replacing what qara_cli_reg_scraper's own (soon-to-be-retired)
 * {@code status}/{@code summary} CLI commands used to compute locally.
 */
@Singleton
public class StatusService {

    private final ScrapedDocumentRepository documentRepository;
    private final ScrapeRunRepository runRepository;
    private final SourceEstimateRepository estimateRepository;

    public StatusService(
            ScrapedDocumentRepository documentRepository,
            ScrapeRunRepository runRepository,
            SourceEstimateRepository estimateRepository
    ) {
        this.documentRepository = documentRepository;
        this.runRepository = runRepository;
        this.estimateRepository = estimateRepository;
    }

    public SourceStatusDTO forSource(String regulation, String source) {
        long documents = documentRepository.countByRegulationAndSource(regulation, source);

        SourceStatusDTO.SourceStatusDTOBuilder builder = SourceStatusDTO.builder()
                .regulation(regulation)
                .source(source)
                .documents(documents);

        runRepository.findFirstByRegulationAndSourceOrderByStartedAtDesc(regulation, source).ifPresent(run -> {
            builder.lastRunId(run.getRunId());
            builder.lastFinishedAt(run.getFinishedAt());
            builder.lastStatus(run.getStatus());
            builder.lastErrors(run.getErrors());
        });

        estimateRepository.findByRegulationAndSource(regulation, source).ifPresent(estimate -> {
            builder.totalAvailable(estimate.getTotalAvailable());
            builder.estimateNote(estimate.getNote());
            // Derived live from the current `documents` count above, not read back off
            // estimate.getRemaining() — that field is only as fresh as the last upsert()
            // (qara_cli_reg_scraper's pre-flight estimate step, then again once a run
            // finishes; see SourceEstimateService's javadoc), so while a run is actively
            // fetching, `documents` climbs in real time (each fetch syncs immediately) but
            // a stored `remaining` would sit frozen at the pre-run snapshot the whole time —
            // exactly the "documents going up, not yet retrieved not going down" gap
            // reported live against a running fda:guidance job. estimate.getAlreadyKnown()'s
            // own note ("the all-time local count, not scoped to any particular window")
            // confirms alreadyKnown was always meant to equal this same live count, so
            // recomputing here changes nothing about a *finished* run's numbers — only
            // makes an *in-progress* run's numbers stop lying.
            if (estimate.getTotalAvailable() != null) {
                builder.remaining((int) Math.max(0L, estimate.getTotalAvailable() - documents));
            }
        });

        return builder.build();
    }
}
