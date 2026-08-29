package com.qaralink.regscraper.svc.workload;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class GenericWorkloadResult implements WorkloadResult {
    @NonNull
    private Workload workload;
    @NonNull
    private String workloadProvider;
    private short exitCode;
    private String displayMessage;
    private String diagnosticMessage;

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
