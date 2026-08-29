package com.qaralink.regscraper.svc.workload;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface WorkloadOrchestrator {

    <T> CompletableFuture<T> submitWorkloadAndProcess(Workload workload, Function<WorkloadResult, T> processor) throws IOException;
}
