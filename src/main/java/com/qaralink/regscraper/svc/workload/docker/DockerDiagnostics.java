package com.qaralink.regscraper.svc.workload.docker;

/**
 * Builds a compact diagnostic message for a finished Docker container run.
 * Docker's model is simple — {@code docker wait} always returns a definitive
 * exit code — unlike a Kubernetes Pod's more ambiguous states (see
 * {@code k8s.PodDiagnostics}).
 */
public final class DockerDiagnostics {

    private DockerDiagnostics() {
    }

    public static String build(short exitCode, String logsTail) {
        StringBuilder sb = new StringBuilder("exit=").append(exitCode);
        if (logsTail != null && !logsTail.isBlank()) {
            sb.append('\n').append(logsTail.strip());
        }
        return sb.toString();
    }
}
