package com.qaralink.regscraper.svc;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.nio.file.Path;

/**
 * Where qara_cli_reg_scraper's own storage root is mounted, from THIS service's point of view —
 * i.e. the same convention {@code qaralink.docker.jobs.shared-dir} uses for config.yaml
 * (see WorkloadConfigService), but for the actual scraped document bytes rather than config.
 * <p>
 * Every {@code storagePath} recorded on a ScrapedDocumentEntity (e.g.
 * "fda/clearances_510k/documents/K252474/summary/current.pdf") is already relative to the CLI's
 * own {@code storage.local.root} (its config.yaml, "./data" by default) — this is only valid
 * when the CLI is configured with {@code storage.backend: local} and this service's container
 * mounts that same directory. Not exercised against S3/Azure Blob/SharePoint backends yet — a
 * document scraped through one of those has a storagePath this service has no way to resolve,
 * and DocumentStorageService fails clearly rather than guessing.
 */
@Data
@ConfigurationProperties("qaralink.storage.local")
public class DocumentStorageConfiguration {
    @NotBlank
    private String root = "./data";

    public Path getRootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }
}
