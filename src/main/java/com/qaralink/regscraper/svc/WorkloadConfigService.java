package com.qaralink.regscraper.svc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.qaralink.regscraper.svc.workload.docker.DockerWorkloadOrchestratorConfiguration;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reads/writes qara_cli_reg_scraper's {@code config.yaml} OVERRIDE file on the shared
 * volume (see {@code DockerWorkloadOrchestratorConfiguration.sharedVolume} /
 * {@code DockerWorkloadOrchestrator.buildContainerEnv}'s {@code QARA_REG_SCRAPER_CONFIG_FILE}) —
 * deliberately never a full snapshot of the CLI's effective settings, only whatever an
 * operator has explicitly {@code PUT}. The CLI's own existing config precedence (its own
 * field defaults -> this file -> env vars -> {@code .env}, see qara_cli_reg_scraper's
 * config.py) already does the real merging; duplicating that logic or the CLI's schema
 * here would just be a second, driftable copy of its defaults — the exact operational
 * nightmare this design avoids.
 * <p>
 * Only the Docker provider's shared directory is used today
 * ({@code qaralink.docker.jobs.shared-dir}) — under {@code EXECUTION_PROVIDER=kubernetes}
 * this would need to write to the k8s PVC's shared-dir instead
 * ({@code qaralink.k8s.jobs.shared-dir}), not exercised or verified in this environment
 * (no live cluster), same caveat as the rest of the Kubernetes provider.
 */
@Singleton
public class WorkloadConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkloadConfigService.class);
    private static final String CONFIG_FILENAME = "config.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Path sharedDir;

    public WorkloadConfigService(DockerWorkloadOrchestratorConfiguration dockerConfig) {
        this.sharedDir = dockerConfig.getSharedDirPath();
    }

    /** The overrides currently in effect — empty if nothing has ever been {@code PUT}, in
     * which case the CLI falls back entirely to its own built-in defaults. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get() {
        Path path = configPath();
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = yamlMapper.readValue(path.toFile(), Map.class);
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    /** Replaces the override file wholesale — atomic write (temp file + rename), same
     * convention qara_cli_reg_scraper's own storage writes use. {@code null} clears every
     * override (the CLI then uses only its own defaults). */
    public Map<String, Object> update(Map<String, Object> overrides) {
        Map<String, Object> toWrite = overrides != null ? overrides : new LinkedHashMap<>();
        Path path = configPath();
        try {
            Files.createDirectories(Objects.requireNonNull(path.getParent()));
            Path tmp = Files.createTempFile(path.getParent(), CONFIG_FILENAME, ".tmp");
            yamlMapper.writeValue(tmp.toFile(), toWrite);
            // World-readable on purpose: this service (root inside its own container) writes
            // the file, but every CLI container that reads it runs as its own non-root user
            // (uid 1000, see qara_cli_reg_scraper's Dockerfile) - Java's default file-creation
            // mode (rw-------) left the file unreadable to anything but this service itself,
            // discovered live (a job failed with PermissionError reading exactly this file).
            // Not a secrets file, so world-readable is the right call, not just "good enough".
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-r--r--"));
            } catch (UnsupportedOperationException e) {
                LOG.warn("POSIX permissions not supported on this filesystem - {} may not be readable by spawned containers", path);
            }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Updated {} ({} top-level keys)", path, toWrite.size());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + path, e);
        }
        return get();
    }

    private Path configPath() {
        return sharedDir.resolve(CONFIG_FILENAME);
    }
}
