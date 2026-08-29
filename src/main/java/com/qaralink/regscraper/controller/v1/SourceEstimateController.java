package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.exceptions.SourceEstimateNotFoundException;
import com.qaralink.regscraper.model.dto.SourceEstimateDTO;
import com.qaralink.regscraper.svc.SourceEstimateService;
import com.qaralink.rest.ApiResponse;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller("/v1/source-estimates")
@Tag(name = "Source Estimates", description = "Latest 'what's left to do' snapshot per source — replaced in place, not historical.")
public class SourceEstimateController {

    private final SourceEstimateService service;

    public SourceEstimateController(SourceEstimateService service) {
        this.service = service;
    }

    @Put("/{regulation}/{source}")
    @Operation(summary = "Replace the estimate snapshot for a source", description = "Mirrors Manifest.write_estimate's overwrite-in-place semantics.")
    public ApiResponse<SourceEstimateDTO> upsert(
            @PathVariable String regulation, @PathVariable String source, @Body SourceEstimateDTO body
    ) {
        body.setRegulation(regulation);
        body.setSource(source);
        return ApiResponse.ok(service.upsert(body));
    }

    @Get("/{regulation}/{source}")
    @Operation(summary = "The current estimate snapshot for a source")
    public ApiResponse<SourceEstimateDTO> get(
            @PathVariable String regulation, @PathVariable String source
    ) throws SourceEstimateNotFoundException {
        return service.find(regulation, source)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new SourceEstimateNotFoundException(
                        "No estimate recorded yet for " + regulation + ":" + source));
    }
}
