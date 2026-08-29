package com.qaralink.regscraper.svc.workload.k8s;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.nio.file.Path;

@Data
@ConfigurationProperties("qaralink.k8s.jobs")
public class K8sWorkloadOrchestratorConfiguration {
    private String namespace;
    @NotBlank
    private String managedBy;
    @NotBlank
    private String template;
    private Path sharedDir;

    public String getJobsNamespace() {
        if (namespace != null && !namespace.isBlank()) {
            return namespace;
        }
        return KubernetesContext.getServiceNamespace();
    }
}
