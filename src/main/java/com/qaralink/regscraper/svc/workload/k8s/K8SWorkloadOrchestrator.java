package com.qaralink.regscraper.svc.workload.k8s;

import com.qaralink.regscraper.svc.workload.*;
import com.qaralink.regscraper.util.CaseFormatUtils;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Orchestrates Kubernetes {@code batch/v1 Job} workloads in a crash-safe, stateless way by
 * treating the Kubernetes API as the single source of truth for job lifecycle and recovery.
 * <p>
 * Ported from opc_svc_ai's {@code K8SWorkloadOrchestrator} — the Job-templating/labeling/
 * submission logic is unchanged; the only real adaptation is dropping the
 * {@code QaraContext}-specific bits (this domain has none).
 */
@Singleton
@Requires(property = "qaralink.execution.provider", value = "kubernetes", defaultValue = "kubernetes")
public class K8SWorkloadOrchestrator implements WorkloadOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(K8SWorkloadOrchestrator.class);

    private final ApplicationContext applicationContext;
    private final K8sWorkloadOrchestratorConfiguration config;
    private final K8sPodCompletionHandler completionHandler;
    private final Path sharedDir;
    private final K8sJobManagementService jobManagementService;
    private final WorkEnvironmentService workEnvironmentService;

    public K8SWorkloadOrchestrator(
            ApplicationContext applicationContext,
            K8sWorkloadOrchestratorConfiguration config,
            K8sPodCompletionHandler completionHandler,
            K8sJobManagementService jobManagementService,
            WorkEnvironmentService workEnvironmentService
    ) {
        this.applicationContext = applicationContext;
        this.config = Objects.requireNonNull(config);
        this.completionHandler = completionHandler;
        this.sharedDir = config.getSharedDir();
        this.jobManagementService = jobManagementService;
        this.workEnvironmentService = workEnvironmentService;
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
        String rawTemplate = Objects.requireNonNull(config.getTemplate(), "Workload template not defined");
        V1Job job = Yaml.loadAs(rawTemplate, V1Job.class);
        if (job.getMetadata() == null) {
            job.setMetadata(new V1ObjectMeta());
        }
        job.getMetadata().setName(KubernetesUtils.canonicalize(workload.getId()));
        job.getMetadata().setNamespace(config.getJobsNamespace());
        V1Container container = Optional.of(job)
                .map(V1Job::getSpec)
                .map(V1JobSpec::getTemplate)
                .map(V1PodTemplateSpec::getSpec)
                .map(V1PodSpec::getContainers)
                .map(List::getFirst)
                .orElseThrow(() -> new IllegalStateException(
                        "No container found in the job template. Review qaralink.k8s.jobs.template"));
        container.setImage(imageConfig.getImage());
        container.setName(KubernetesUtils.canonicalize(workload.getType() + "-container"));
        K8sWorkloadLabels labels = K8sWorkloadLabels.from(workload);
        labels.applyTo(job);
        applyAnnotations(job, workload.getAnnotations());
        setupCommand(workload, workEnvironment, container);
        setupEnv(workload, container);

        LOG.info("Prepared K8s job: \n{}", Yaml.dump(job));
        return submitAndWait(job, labels)
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
                .whenComplete((unused, throwable) -> workEnvironmentService.cleanupWorkdir(workEnvironment));
    }

    private void setupCommand(Workload workload, WorkEnvironment workEnvironment, V1Container container) {
        List<String> command = workEnvironmentService.resolveCommand(workload, workEnvironment);
        if (command != null) {
            LOG.info("Setting job command to {}", command);
            container.setCommand(command);
        }
        List<String> args = workEnvironmentService.resolveArgs(workload, workEnvironment);
        if (args != null) {
            LOG.info("Setting job args to {}", args);
            container.setArgs(args);
        }
    }

    /**
     * Job-specific extra env vars (e.g. QARA_REG_SCRAPER_RETRY_BUDGET_MINUTES —
     * see ScrapeJobService#triggerRetry) on top of whatever the pod template's own
     * {@code envFrom: secretRef} already provides (see application.yml's
     * {@code qaralink.k8s.jobs.template}). NOT verified against a real cluster —
     * this environment only exercises the Docker provider (see this repo's README,
     * "Not verified") — first per-job env-var plumbing on the K8s side; the Docker
     * orchestrator's own {@code buildContainerEnv} is the tested sibling.
     */
    private void setupEnv(Workload workload, V1Container container) {
        if (workload.getEnv() == null || workload.getEnv().isEmpty()) {
            return;
        }
        if (container.getEnv() == null) {
            container.setEnv(new ArrayList<>());
        }
        workload.getEnv().forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                container.getEnv().add(new V1EnvVar().name(key).value(value));
            }
        });
    }

    private void applyAnnotations(V1Job job, Map<String, String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        if (job.getMetadata().getAnnotations() == null) {
            job.getMetadata().setAnnotations(new HashMap<>());
        }
        job.getMetadata().getAnnotations().putAll(annotations);

        if (job.getSpec().getTemplate().getMetadata() == null) {
            job.getSpec().getTemplate().setMetadata(new V1ObjectMeta());
        }
        if (job.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
            job.getSpec().getTemplate().getMetadata().setAnnotations(new HashMap<>());
        }
        job.getSpec().getTemplate().getMetadata().getAnnotations().putAll(annotations);
    }

    public CompletableFuture<K8sPodResult> submitAndWait(V1Job job, K8sWorkloadLabels labels) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(job.getMetadata(), "job.metadata");

        String name = job.getMetadata().getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("job.metadata.name must be set");
        }

        try {
            jobManagementService.startJobIfNotRunning(config.getJobsNamespace(), job);
            return completionHandler.awaitPod(labels);
        } catch (ApiException e) {
            throw new IllegalStateException("Failed to submit Job '" + name + "': " + safeApiError(e), e);
        }
    }

    private static String safeApiError(ApiException e) {
        String body = e.getResponseBody();
        if (body == null || body.isBlank()) return "HTTP " + e.getCode();
        return "HTTP " + e.getCode() + " " + body;
    }
}
