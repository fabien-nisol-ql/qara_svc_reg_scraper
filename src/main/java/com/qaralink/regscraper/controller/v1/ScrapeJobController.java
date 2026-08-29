package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.model.api.TriggerScrapeJobRequest;
import com.qaralink.regscraper.model.dto.ScrapeJobDTO;
import com.qaralink.regscraper.model.dto.ScrapeJobHistoryDTO;
import com.qaralink.regscraper.svc.ScrapeJobService;
import com.qaralink.regscraper.svc.security.AccessControl;
import com.qaralink.rest.ApiResponse;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

@Controller("/v1/jobs")
@Tag(name = "Scrape Jobs", description = "Trigger and track qara_cli_reg_scraper runs, launched as a Docker container or a Kubernetes Job.")
public class ScrapeJobController {

    private final ScrapeJobService service;
    private final AccessControl accessControl;

    public ScrapeJobController(ScrapeJobService service, AccessControl accessControl) {
        this.service = service;
        this.accessControl = accessControl;
    }

    @Post("/scrape")
    @Operation(
            summary = "Trigger a scrape job for one or more sources",
            description = "Requires the admin role (see AccessControl) — this is the one endpoint on this "
                    + "controller with no legitimate anonymous caller, unlike the GETs below. Launches "
                    + "qara_cli_reg_scraper `run` with the given sources/flags via the active execution "
                    + "provider (Docker or Kubernetes) and returns immediately with the job's id — poll "
                    + "GET /v1/jobs/{jobId} for status."
    )
    public ApiResponse<ScrapeJobDTO> trigger(@Valid @Body TriggerScrapeJobRequest request, HttpRequest<?> httpRequest) {
        accessControl.requireRole(httpRequest, accessControl.adminRole());
        return ApiResponse.ok(service.trigger(request, "manual"));
    }

    @Get("/{jobId}")
    @Operation(summary = "A job's current status")
    public HttpResponse<ApiResponse<ScrapeJobDTO>> get(@PathVariable String jobId, HttpRequest<?> httpRequest) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        return service.get(jobId)
                .map(dto -> HttpResponse.ok(ApiResponse.ok(dto)))
                .orElseGet(() -> HttpResponse.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.notFound("No job found with id %s", jobId)));
    }

    @Get
    @Operation(
            summary = "List jobs, optionally filtered to one source",
            description = "Sort explicitly (e.g. ?sort=submittedAt,desc) to get most-recently-submitted-first — "
                    + "nothing here imposes a default order. \"source\" is a single \"<regulation>:<source>\" "
                    + "qualified name; a job triggered for several sources at once matches on any of them."
    )
    public Page<ScrapeJobDTO> list(
            @Nullable @QueryValue @Parameter(example = "fda:ecfr") String source,
            Pageable pageable,
            HttpRequest<?> httpRequest
    ) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        return service.search(source, pageable);
    }

    @Get("/{jobId}/history")
    @Operation(summary = "A job's status transition history")
    public Page<ScrapeJobHistoryDTO> history(@PathVariable String jobId, Pageable pageable, HttpRequest<?> httpRequest) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        return service.history(jobId, pageable);
    }
}
