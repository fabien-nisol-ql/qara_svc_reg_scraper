package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.model.dto.ScrapeRunDTO;
import com.qaralink.regscraper.svc.ScrapeRunService;
import com.qaralink.rest.ApiResponse;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller("/v1/runs")
@Tag(name = "Runs", description = "Scrape run summaries — one per qara_cli_reg_scraper `run` invocation.")
public class RunController {

    private final ScrapeRunService service;

    public RunController(ScrapeRunService service) {
        this.service = service;
    }

    @Post
    @Operation(summary = "Upsert a scrape run", description = "Creates or updates the row for a runId — once at the start of a run, again when it finishes.")
    public ApiResponse<ScrapeRunDTO> upsert(@Body ScrapeRunDTO run) {
        return ApiResponse.ok(service.upsert(run));
    }

    @Get
    @Operation(summary = "List runs for a source", description = "Paginated, most recent first by default sort.")
    public Page<ScrapeRunDTO> search(@QueryValue String regulation, @QueryValue String source, Pageable pageable) {
        return service.search(regulation, source, pageable);
    }

    @Get("/latest")
    @Operation(summary = "The most recent run for a source")
    public HttpResponse<ApiResponse<ScrapeRunDTO>> latest(@QueryValue String regulation, @QueryValue String source) {
        return service.latest(regulation, source)
                .map(dto -> HttpResponse.ok(ApiResponse.ok(dto)))
                .orElseGet(() -> HttpResponse.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.notFound("No run recorded yet for %s:%s", regulation, source)));
    }
}
