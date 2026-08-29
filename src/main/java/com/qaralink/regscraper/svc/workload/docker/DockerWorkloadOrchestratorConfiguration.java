package com.qaralink.regscraper.svc.workload.docker;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.nio.file.Path;
import java.time.Duration;

@Data
@ConfigurationProperties("qaralink.docker.jobs")
public class DockerWorkloadOrchestratorConfiguration {
    /**
     * The path THIS service's own Java code reads/writes directly (e.g. the shared
     * qara_cli_reg_scraper config.yaml override — see WorkloadConfigService) — must be a real
     * mounted directory inside this service's own container, normally the SAME value as
     * {@link #containerSharedDir} (mirrors opc_svc_ai's own documented convention: "this
     * service and its workload Pods mount the same PVC at the same path").
     */
    @NotBlank
    private String sharedDir;
    private String containerSharedDir = "/shared/qaralink/job-data/";
    /**
     * The value passed straight through to spawned sibling containers' {@code docker run -v
     * <this>:<containerSharedDir>} — NOT {@link #sharedDir}'s own in-container path string.
     * Under docker-outside-of-docker (a mounted /var/run/docker.sock), that {@code docker run}
     * executes against the HOST's real daemon, which resolves SRC by asking the daemon for it
     * directly - a path INSIDE this service's own container (like {@link #sharedDir}) would be
     * silently invisible to a spawned container, since the daemon has no notion of "this
     * container's filesystem" (discovered live: this was the case before shared-volume support
     * was added). Both a Docker-managed named volume AND an absolute HOST filesystem path work
     * equally well here, for the same reason - the daemon resolves either one directly, not
     * relative to whichever container is asking. QARA_IAC_LOCAL_DOCKER currently passes an
     * absolute host path (a bind mount into its own repo, so scraped documents survive a
     * `docker compose down -v`/environment reset instead of forcing a full re-scrape) - see its
     * docker-compose.yml's own comment on DOCKER_JOB_SHARED_VOLUME. Either way, mount the SAME
     * thing into this service's own compose entry, at {@link #sharedDir}'s path, so both sides
     * see the identical files.
     */
    private String sharedVolume;
    private String network;
    // Deliberately NOT a Map<String,String> bound straight from YAML: Micronaut's global
    // micronaut.property.naming.strategy: KEBAB_CASE (see application.yml) rewrites literal
    // YAML map keys the same way it rewrites property paths, so a key written here as
    // `QARA_REG_SCRAPER_SERVICE__BASE_URL` would actually bind as `qara-reg-scraper-service-base-url`
    // - silently wrong for a workload container that needs the exact env var name its own
    // process reads (discovered live: the CLI's first env var passed this way, its Postgres
    // DSN, never actually reached it - since retired, the CLI no longer touches Postgres
    // directly at all, see qara_cli_reg_scraper's README/manifest.py). One typed field per
    // real passthrough var instead; DockerWorkloadOrchestrator assembles the final env map
    // with the literal, correctly-cased key.
    private String cliServiceBaseUrl;
    private String envFile;
    /**
     * Whether to `docker rm` the container after each run — after {@link #ttl} has passed,
     * not immediately. Docker has no built-in equivalent of Kubernetes' Job
     * {@code ttlSecondsAfterFinished} (a grace period before cleanup, giving you time to
     * `docker logs`/`docker inspect` a finished container); removing it the instant the
     * workload's future completes made a real container impossible to inspect after the fact
     * (discovered live: a job "succeeding" in ~5s left nothing to look at once its own
     * completion handler ran). {@link com.qaralink.regscraper.svc.workload.docker.DockerWorkloadOrchestrator}
     * schedules the removal {@link #ttl} later instead of doing it inline, mirroring
     * {@code qaralink.k8s.jobs.ttl}'s real effect on the Kubernetes side.
     */
    private boolean autoRemove = true;
    private Duration ttl = Duration.ofSeconds(300);
    private String imagePullPolicy = "IfNotPresent";
    private String namePrefix = "qara-reg-scraper-";
    private Duration waitTimeout = Duration.ofHours(2);

    public Path getSharedDirPath() {
        return Path.of(sharedDir);
    }
}
