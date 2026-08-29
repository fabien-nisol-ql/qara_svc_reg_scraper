package com.qaralink.regscraper.svc.workload.k8s;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import io.micronaut.context.annotation.Requires;
import io.micronaut.kubernetes.client.informer.InformerConfiguration;
import io.micronaut.kubernetes.client.informer.SharedIndexInformerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches Pods in the jobs namespace and resolves the {@link CompletableFuture} a
 * {@link K8SWorkloadOrchestrator} caller is waiting on once the workload Pod reaches a
 * terminal phase (Succeeded/Failed) and its exit code is known.
 * <p>
 * Ported from opc_svc_ai — gated the same way as {@link K8SWorkloadOrchestrator} itself
 * (only instantiated under {@code qaralink.execution.provider=kubernetes}) so a
 * docker-provider deployment never needs Kubernetes informer connectivity at all.
 * Dropped the debug-level YAML-diff logging opc_svc_ai's version had between pod updates
 * (needs an extra diff library for a debug convenience, not essential here).
 */
@Singleton
@Requires(property = "qaralink.execution.provider", value = "kubernetes")
public class K8sPodCompletionHandler implements ResourceEventHandler<V1Pod> {

    private static final Logger LOG = LoggerFactory.getLogger(K8sPodCompletionHandler.class);

    private final Map<K8sWorkloadLabels, CompletableFuture<K8sPodResult>> waiting = new ConcurrentHashMap<>();
    private final SharedIndexInformerFactory sharedIndexInformerFactory;
    private final K8sWorkloadOrchestratorConfiguration config;
    private final InformerConfiguration informerConfiguration;

    public K8sPodCompletionHandler(SharedIndexInformerFactory sharedIndexInformerFactory,
                                    K8sWorkloadOrchestratorConfiguration config,
                                    InformerConfiguration informerConfiguration) {
        this.sharedIndexInformerFactory = sharedIndexInformerFactory;
        this.config = config;
        this.informerConfiguration = informerConfiguration;
    }

    @PostConstruct
    void initializeInformer() {
        SharedIndexInformer<V1Pod> podInformer =
                sharedIndexInformerFactory.sharedIndexInformerFor(
                        V1Pod.class,
                        V1PodList.class,
                        "pods",
                        "",
                        config.getJobsNamespace(),
                        null,
                        informerConfiguration.getResyncPeriod().map(Duration::toMillis).orElse(60_000L),
                        true
                );
        podInformer.addEventHandler(this);
        LOG.info("Configured K8s pod informer for namespace {}", config.getJobsNamespace());
    }

    CompletableFuture<K8sPodResult> awaitPod(K8sWorkloadLabels labels) {
        CompletableFuture<K8sPodResult> future = new CompletableFuture<>();
        CompletableFuture<K8sPodResult> previous = waiting.putIfAbsent(labels, future);
        if (previous != null) {
            return previous;
        }
        future.whenComplete((pod, error) -> waiting.remove(labels, future));
        return future;
    }

    @Override
    public void onAdd(V1Pod pod) {
        handlePodState(pod);
    }

    @Override
    public void onUpdate(V1Pod oldPod, V1Pod newPod) {
        handlePodState(newPod);
    }

    private void handlePodState(V1Pod pod) {
        LOG.debug("K8S observed pod {}", Yaml.dump(pod));

        K8sWorkloadLabels labels = K8sWorkloadLabels.from(pod);
        if (labels == null) {
            return;
        }

        CompletableFuture<K8sPodResult> future = waiting.get(labels);
        if (future == null || future.isDone()) {
            return;
        }

        String phase = Optional.ofNullable(pod)
                .map(V1Pod::getStatus)
                .map(V1PodStatus::getPhase)
                .orElse("Unknown");

        if (!isTerminalPhase(phase)) {
            return;
        }

        LOG.info("K8S Pod {} status {}, diagnostic={}", labels, phase, PodDiagnostics.build(pod));

        V1ContainerStatus containerStatus = getRelevantContainerStatus(pod);
        if (containerStatus == null) {
            LOG.warn("Pod {} is in terminal phase {} but no container status is available yet. diagnostic={}",
                    labels, phase, PodDiagnostics.build(pod));
            return;
        }

        Integer exitCode = Optional.ofNullable(containerStatus.getState())
                .map(V1ContainerState::getTerminated)
                .map(V1ContainerStateTerminated::getExitCode)
                .orElse(null);

        if (exitCode != null) {
            complete(labels, future, exitCode.shortValue(), pod);
            return;
        }

        Integer lastExitCode = Optional.ofNullable(containerStatus.getLastState())
                .map(V1ContainerState::getTerminated)
                .map(V1ContainerStateTerminated::getExitCode)
                .orElse(null);

        if (lastExitCode != null) {
            complete(labels, future, lastExitCode.shortValue(), pod);
            return;
        }

        if (isDefinitelyNeverGoingToProduceExitCode(containerStatus, pod)) {
            LOG.warn("Pod {} reached terminal phase {} but no exit code is available for container {}. diagnostic={}",
                    labels, phase, containerStatus.getName(), PodDiagnostics.build(pod));
            complete(labels, future, (short) -1, pod);
        }
    }

    @Override
    public void onDelete(V1Pod pod, boolean finalStateUnknown) {
        K8sWorkloadLabels labels = K8sWorkloadLabels.from(pod);
        if (labels == null) {
            return;
        }
        LOG.info("K8S Pod {} DELETED diagnostic={}", labels, PodDiagnostics.build(pod));

        CompletableFuture<K8sPodResult> future = waiting.remove(labels);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new IllegalStateException(
                    "Pod %s was deleted before completion".formatted(
                            Objects.requireNonNull(pod.getMetadata(), "V1Pod should have metadata").getName())));
        }
    }

    private void complete(K8sWorkloadLabels labels, CompletableFuture<K8sPodResult> future, short exitCode, V1Pod pod) {
        if (waiting.remove(labels, future)) {
            future.complete(K8sPodResult.builder()
                    .pod(pod)
                    .exitCode(exitCode)
                    .diagnosticMessage(PodDiagnostics.build(pod))
                    .build());
        }
    }

    private boolean isTerminalPhase(String phase) {
        return "Succeeded".equals(phase) || "Failed".equals(phase);
    }

    private V1ContainerStatus getRelevantContainerStatus(V1Pod pod) {
        List<V1ContainerStatus> statuses = Optional.ofNullable(pod)
                .map(V1Pod::getStatus)
                .map(V1PodStatus::getContainerStatuses)
                .orElse(null);
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return statuses.get(statuses.size() - 1);
    }

    private boolean isDefinitelyNeverGoingToProduceExitCode(V1ContainerStatus cs, V1Pod pod) {
        V1ContainerState state = cs.getState();
        if (state == null) {
            return false;
        }

        V1ContainerStateWaiting waitingState = state.getWaiting();
        if (waitingState != null) {
            String reason = waitingState.getReason();
            if (reason != null) {
                switch (reason) {
                    case "ImagePullBackOff", "ErrImagePull", "CreateContainerConfigError",
                         "CreateContainerError", "CrashLoopBackOff", "RunContainerError",
                         "StartError", "ContainerCannotRun":
                        return true;
                    default:
                        break;
                }
            }
        }

        V1ContainerStateTerminated terminated = state.getTerminated();
        if (terminated != null && terminated.getExitCode() != null) {
            return false;
        }

        if ("Failed".equals(Optional.ofNullable(pod.getStatus()).map(V1PodStatus::getPhase).orElse(null))) {
            Integer lastExitCode = Optional.ofNullable(cs.getLastState())
                    .map(V1ContainerState::getTerminated)
                    .map(V1ContainerStateTerminated::getExitCode)
                    .orElse(null);
            if (lastExitCode != null) {
                return false;
            }
            return waitingState != null || terminated != null;
        }

        return false;
    }
}
