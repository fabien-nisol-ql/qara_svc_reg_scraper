package com.qaralink.regscraper.svc.workload.k8s;

import io.kubernetes.client.openapi.models.V1Pod;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class K8sPodResult {
    private V1Pod pod;
    private short exitCode;
    private String diagnosticMessage;
}
