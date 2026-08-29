package com.qaralink.regscraper.svc.workload.k8s;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class KubernetesContext {

    private static final Path SA_NAMESPACE_FILE =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    private static volatile String cachedNamespace;

    private KubernetesContext() {
    }

    public static String getServiceNamespace() {
        String ns = cachedNamespace;
        if (ns != null && !ns.isBlank()) {
            return ns;
        }

        // 1) Fast path: mounted file (no network, works in-cluster)
        try {
            if (Files.exists(SA_NAMESPACE_FILE)) {
                ns = Files.readString(SA_NAMESPACE_FILE, StandardCharsets.UTF_8).trim();
                if (!ns.isBlank()) {
                    cachedNamespace = ns;
                    return ns;
                }
            }
        } catch (IOException ignored) {
            // fall through
        }

        // 2) Optional fallback: env var injected via the Downward API
        ns = System.getenv("POD_NAMESPACE");
        if (ns != null && !ns.isBlank()) {
            cachedNamespace = ns.trim();
            return cachedNamespace;
        }

        // 3) Sensible default for local/non-k8s runs
        cachedNamespace = "default";
        return cachedNamespace;
    }
}
