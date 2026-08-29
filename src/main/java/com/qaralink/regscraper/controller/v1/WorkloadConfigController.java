package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.svc.WorkloadConfigService;
import com.qaralink.rest.ApiResponse;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

import java.util.Map;

@Controller("/v1/workload-config")
@Tag(
        name = "Workload Config",
        description = "qara_cli_reg_scraper's config.yaml override, shared with it via a mounted "
                + "volume — not a full effective-settings snapshot, only what's been explicitly "
                + "overridden here. The CLI's own existing defaults/precedence merges this against "
                + "everything else; see WorkloadConfigService for why nothing more is duplicated here."
)
public class WorkloadConfigController {

    private final WorkloadConfigService service;

    public WorkloadConfigController(WorkloadConfigService service) {
        this.service = service;
    }

    @Get
    @Operation(
            summary = "Current config.yaml overrides",
            description = "Empty if nothing has ever been PUT — the CLI then uses only its own built-in defaults."
    )
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(service.get());
    }

    @Put
    @Operation(
            summary = "Replace the config.yaml overrides",
            description = "Written directly to the shared volume, picked up by the next launched "
                    + "job — no restart needed. Not validated against qara_cli_reg_scraper's own "
                    + "schema; a bad key only surfaces when a job next runs and fails (visible via "
                    + "its status/diagnostic message). An empty or omitted body clears every override."
    )
    public ApiResponse<Map<String, Object>> update(@Nullable @Body Map<String, Object> overrides) {
        return ApiResponse.ok(service.update(overrides));
    }
}
