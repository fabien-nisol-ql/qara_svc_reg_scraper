package com.qaralink.regscraper.svc.workload.docker;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DockerRunResult {
    private short exitCode;
    private String diagnosticMessage;
}
