package com.qaralink.regscraper.svc.workload;

public interface WorkloadResult {

    Workload getWorkload();

    boolean isSuccess();

    short getExitCode();

    String getDisplayMessage();

    String getDiagnosticMessage();

    /** Which orchestrator implementation ran this ("docker" | "kubernetes"), or the image, per provider. */
    String getWorkloadProvider();
}
