package com.qaralink.regscraper.svc.workload.docker;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommandResult {
    private int exitCode;
    private String stdout;
    private String stderr;
}
