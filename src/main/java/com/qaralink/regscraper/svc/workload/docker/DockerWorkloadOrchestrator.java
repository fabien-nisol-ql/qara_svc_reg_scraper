package com.qaralink.regscraper.svc.workload.docker;

import com.qaralink.regscraper.svc.workload.*;
import com.qaralink.regscraper.util.CaseFormatUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Orchestrates workloads as plain {@code docker run} containers on the host running this
 * service, for {@code qaralink.execution.provider=docker} deployments (typically local/
 * single-node use without a Kubernetes cluster). The Docker-provider sibling of
 * {@link com.qaralink.regscraper.svc.workload.k8s.K8SWorkloadOrchestrator}.
 * <p>
 * Ported from opc_svc_ai's {@code DockerWorkloadOrchestrator} — the shared-directory
 * plumbing (a Docker volume mounted at the same path in this service and in every spawned
 * container, matching opc_svc_ai's own convention) is used by qara_cli_reg_scraper's {@code run}
 * for exactly one thing today: an optional {@code config.yaml} override this service's
 * {@code WorkloadConfigService} reads/writes via {@code GET}/{@code PUT /v1/workload-config} —
 * not the input/output-parameter machinery opc_svc_ai's own AI workloads use, which this port
 * deliberately dropped (see the package's other classes).
 */
@Singleton
@Requires(property = "qaralink.execution.provider", value = "docker")
public class DockerWorkloadOrchestrator implements WorkloadOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(DockerWorkloadOrchestrator.class);

    private final ApplicationContext applicationContext;
    private final DockerWorkloadOrchestratorConfiguration config;
    private final DockerContainerManagementService containerManagementService;
    private final WorkEnvironmentService workEnvironmentService;
    private final ExecutorService blockingExecutor;
    private final TaskScheduler taskScheduler;
    private final Path sharedDir;

    public DockerWorkloadOrchestrator(
            ApplicationContext applicationContext,
            DockerWorkloadOrchestratorConfiguration config,
            DockerContainerManagementService containerManagementService,
            WorkEnvironmentService workEnvironmentService,
            @Named(TaskExecutors.BLOCKING) ExecutorService blockingExecutor,
            TaskScheduler taskScheduler
    ) {
        this.applicationContext = applicationContext;
        this.config = Objects.requireNonNull(config);
        this.containerManagementService = containerManagementService;
        this.workEnvironmentService = workEnvironmentService;
        this.blockingExecutor = blockingExecutor;
        this.taskScheduler = taskScheduler;
        this.sharedDir = config.getSharedDirPath();
    }

    @PostConstruct
    void checkDockerAvailable() {
        containerManagementService.checkDaemonAvailable();
    }

    @Override
    public <T> CompletableFuture<T> submitWorkloadAndProcess(Workload workload, Function<WorkloadResult, T> processor) throws IOException {
        Objects.requireNonNull(workload);
        WorkEnvironment workEnvironment = workEnvironmentService.prepare(sharedDir, workload);
        LOG.info("Workload {}: created work environment {}", workload, workEnvironment);

        WorkloadImageConfig imageConfig = applicationContext.getBean(
                WorkloadImageConfig.class,
                Qualifiers.byName(CaseFormatUtils.toKebabCase(workload.getType()))
        );
        String containerName = config.getNamePrefix() + com.qaralink.regscraper.svc.workload.k8s.KubernetesUtils.canonicalize(workload.getId());
        List<String> command = workEnvironmentService.resolveCommand(workload, workEnvironment);
        List<String> args = workEnvironmentService.resolveArgs(workload, workEnvironment);

        DockerRunSpec spec = DockerRunSpec.builder()
                .name(containerName)
                .image(imageConfig.getImage())
                .command(command)
                .args(args)
                .sharedVolume(config.getSharedVolume())
                .containerSharedDir(config.getContainerSharedDir())
                .network(config.getNetwork())
                .env(buildContainerEnv(workload))
                .envFile(config.getEnvFile())
                .imagePullPolicy(config.getImagePullPolicy())
                .build();
        LOG.info("Prepared docker run spec: {}", spec);

        // Off the event loop — `docker wait` blocks for the container's full lifetime.
        return CompletableFuture
                .supplyAsync(() -> runAndAwait(spec), blockingExecutor)
                .thenApply(result -> workEnvironmentService.buildResult(
                        workload, imageConfig.getImage(), result.getExitCode(), result.getDiagnosticMessage()))
                .thenApply(r -> {
                    try {
                        return processor.apply(r);
                    } catch (Exception e) {
                        LOG.error("Failed to process workload result", e);
                        throw e;
                    }
                })
                .whenComplete((unused, throwable) -> {
                    workEnvironmentService.cleanupWorkdir(workEnvironment);
                    if (config.isAutoRemove()) {
                        // Delayed, not immediate — see the javadoc on
                        // DockerWorkloadOrchestratorConfiguration.autoRemove for why:
                        // this is Docker's equivalent of Kubernetes Jobs'
                        // ttlSecondsAfterFinished, a grace period to `docker logs`/
                        // `docker inspect` a finished container before it's gone.
                        LOG.info("Container '{}' will be removed in {}", spec.getName(), config.getTtl());
                        taskScheduler.schedule(config.getTtl(), () -> containerManagementService.removeQuietly(spec.getName()));
                    }
                });
    }

    /**
     * Builds the workload container's env map with literal, hardcoded key names — never
     * bound straight from a YAML property path — since qara_cli_reg_scraper reads exact
     * env var names (e.g. {@code QARA_REG_SCRAPER_SERVICE__BASE_URL}) that Micronaut's own
     * KEBAB_CASE naming strategy would otherwise silently rewrite. See the comment on
     * {@link DockerWorkloadOrchestratorConfiguration#getCliServiceBaseUrl()}.
     * <p>
     * {@code QARA_REG_SCRAPER_CONFIG_FILE} is set unconditionally, even if
     * {@code <containerSharedDir>config.yaml} doesn't exist yet (nothing's ever been PUT to
     * {@code /v1/workload-config}) — harmless either way, since the CLI's own
     * {@code _load_yaml_defaults()} already tolerates a missing file by returning {@code {}}
     * and falling back entirely to its own built-in defaults. See WorkloadConfigService: this
     * service deliberately never writes a full config, only whatever an operator has
     * explicitly overridden, so the CLI's own existing defaults/overrides precedence does the
     * actual merging — not duplicated here.
     * <p>
     * {@code QARA_REG_SCRAPER_STORAGE__LOCAL__ROOT} is the other half of GET
     * /v1/documents/{id}/content actually working (see DocumentStorageConfiguration's javadoc):
     * without it, the CLI's own {@code storage.local.root} default ("./data") writes each
     * scraped document into the *spawned job container's own ephemeral filesystem* - gone the
     * moment {@link DockerWorkloadOrchestratorConfiguration#isAutoRemove()} cleans it up, and
     * never visible to this service in the first place, which has no mount at that path either.
     * Confirmed live: the metadata still made it into Postgres fine (that's a REST POST, not a
     * file write), so a document looks fully indexed right up until its content is requested,
     * which 404s. Pointing this at a subdirectory of containerSharedDir - the SAME volume
     * already mounted into both this service (see DocumentStorageConfiguration's
     * qaralink.storage.local.root, must be configured to the identical path) and every job
     * container it spawns - gives both sides a shared, durable location instead.
     */
    private Map<String, String> buildContainerEnv(Workload workload) {
        Map<String, String> env = new LinkedHashMap<>();
        if (config.getCliServiceBaseUrl() != null && !config.getCliServiceBaseUrl().isBlank()) {
            env.put("QARA_REG_SCRAPER_SERVICE__BASE_URL", config.getCliServiceBaseUrl());
        }
        if (config.getContainerSharedDir() != null && !config.getContainerSharedDir().isBlank()) {
            String base = config.getContainerSharedDir().endsWith("/")
                    ? config.getContainerSharedDir()
                    : config.getContainerSharedDir() + "/";
            env.put("QARA_REG_SCRAPER_CONFIG_FILE", base + "config.yaml");
            env.put("QARA_REG_SCRAPER_STORAGE__LOCAL__ROOT", base + "documents");
        }
        // Job-specific extras (e.g. QARA_REG_SCRAPER_RETRY_BUDGET_MINUTES for a
        // retry-scheduler-triggered job — see ScrapeJobService#triggerRetry) layered on top,
        // last, so a job-specific value could in principle override one of the base ones above
        // too, though nothing does today. A blank value is skipped, not put as "" — see
        // DockerContainerManagementService's own env-emit step, which drops blank values by
        // design; matching that here rather than emitting one it would silently discard anyway.
        if (workload.getEnv() != null) {
            workload.getEnv().forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    env.put(key, value);
                }
            });
        }
        return env;
    }

    private DockerRunResult runAndAwait(DockerRunSpec spec) {
        try {
            String containerId = containerManagementService.startContainerIfNotRunning(spec);
            LOG.info("Started container '{}' ({}) for '{}', waiting for exit", containerId, spec.getImage(), spec.getName());
            short exitCode = containerManagementService.waitForExit(containerId, config.getWaitTimeout());
            // Captured on every run, not just failures — a 0-exit container with an
            // empty/misconfigured environment (e.g. no service base URL reachable)
            // still needs its own output to be diagnosable once auto-remove deletes
            // the container.
            String logsTail = containerManagementService.tailLogs(containerId, 200);
            LOG.info("Container '{}' for '{}' exited with code {}", containerId, spec.getName(), exitCode);
            return DockerRunResult.builder()
                    .exitCode(exitCode)
                    .diagnosticMessage(DockerDiagnostics.build(exitCode, logsTail))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run docker workload '" + spec.getName() + "'", e);
        }
    }
}
