package com.qaralink.regscraper.svc.workload;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Singular;

import java.nio.file.Path;
import java.util.Map;

@Data
@Builder
public class WorkEnvironment {
    @Singular
    private Map<String, String> parameters;
    @NonNull
    private Path workdir;
}
