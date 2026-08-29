package com.qaralink.regscraper.svc.workload.k8s;

import io.kubernetes.client.openapi.models.*;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public final class PodDiagnostics {

    private PodDiagnostics() {
    }

    public static String build(V1Pod pod) {
        String phase = opt(pod.getStatus(), V1PodStatus::getPhase);
        String node = opt(pod.getSpec(), V1PodSpec::getNodeName);
        String podIp = opt(pod.getStatus(), V1PodStatus::getPodIP);

        boolean jobTrackingFinalizerPresent = Optional.ofNullable(pod.getMetadata())
                .map(V1ObjectMeta::getFinalizers)
                .map(f -> f.contains("batch.kubernetes.io/job-tracking"))
                .orElse(false);

        String containerSig = containerSignature(pod.getStatus());
        String condSig = conditionsSignature(pod.getStatus());

        return String.join("\n",
                "phase=" + nullSafe(phase),
                "node=" + nullSafe(node),
                "podIP=" + nullSafe(podIp),
                "jobTrackingFinalizer=" + jobTrackingFinalizerPresent,
                "containers=" + containerSig,
                "conditions=" + condSig,
                "exitSummary=" + PodExitSummary.from(pod).format()
        );
    }

    private static String containerSignature(V1PodStatus st) {
        if (st == null || st.getContainerStatuses() == null || st.getContainerStatuses().isEmpty()) {
            return "-";
        }
        return st.getContainerStatuses().stream()
                .map(cs -> cs.getName() + ":" + stateSignature(cs.getState()))
                .collect(Collectors.joining(","));
    }

    private static String stateSignature(V1ContainerState s) {
        if (s == null) return "-";
        if (s.getRunning() != null) {
            return "running@" + nullSafe(ts(s.getRunning().getStartedAt()));
        }
        if (s.getWaiting() != null) {
            return "waiting(" + nullSafe(s.getWaiting().getReason()) + ")";
        }
        if (s.getTerminated() != null) {
            V1ContainerStateTerminated t = s.getTerminated();
            return "terminated(exit=" + t.getExitCode()
                    + ",reason=" + nullSafe(t.getReason())
                    + ",at=" + nullSafe(ts(t.getFinishedAt()))
                    + ")";
        }
        return "-";
    }

    private static String conditionsSignature(V1PodStatus st) {
        if (st == null || st.getConditions() == null || st.getConditions().isEmpty()) {
            return "-";
        }
        return st.getConditions().stream()
                .sorted(Comparator.comparing(V1PodCondition::getType, Comparator.nullsLast(String::compareTo)))
                .map(c -> c.getType()
                        + ":" + nullSafe(c.getStatus())
                        + (c.getReason() != null ? ("/" + c.getReason()) : ""))
                .collect(Collectors.joining(","));
    }

    private static String ts(OffsetDateTime t) {
        return t == null ? null : t.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static <T, R> String opt(T obj, java.util.function.Function<T, R> f) {
        if (obj == null) return null;
        R r = f.apply(obj);
        return r == null ? null : String.valueOf(r);
    }
}
