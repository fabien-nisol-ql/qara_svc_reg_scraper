package com.qaralink.regscraper.svc.workload;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import lombok.Data;

/**
 * Per-workload-type image to run, shared by every {@link WorkloadOrchestrator}
 * implementation (Kubernetes, Docker, ...) — the image isn't a Kubernetes concept,
 * only how it's launched is. One entry today: {@code qaralink.workloads.ScraperRun.image}.
 */
@EachProperty("qaralink.workloads")
@Introspected
@Data
public class WorkloadImageConfig {
    private final String name;
    private String image;

    public WorkloadImageConfig(@Parameter String name) {
        this.name = name;
    }
}
