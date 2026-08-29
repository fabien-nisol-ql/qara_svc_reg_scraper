package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.model.dto.RegulationSourceDTO;
import com.qaralink.regscraper.svc.RegulationSourceService;
import com.qaralink.regscraper.svc.security.AccessControl;
import com.qaralink.rest.ApiResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

import java.util.List;

@Controller("/v1/sources")
@Tag(
        name = "Sources",
        description = "What qara_cli_reg_scraper knows how to run, kept in sync by the CLI itself via PUT — see "
                + "RegulationSourceService. Unlike /v1/documents, /v1/status, etc., this isn't backed by "
                + "anything that's ever been scraped; it's what COULD be, so a UI can list every known source "
                + "up front instead of only ones with at least one document already indexed."
)
public class RegulationSourceController {

    private final RegulationSourceService service;
    private final AccessControl accessControl;

    public RegulationSourceController(RegulationSourceService service, AccessControl accessControl) {
        this.service = service;
        this.accessControl = accessControl;
    }

    @Get
    @Operation(summary = "List known sources", description = "Requires the viewer role when authenticated (see "
            + "AccessControl). Optionally filtered to one regulation, e.g. \"fda\".")
    public List<RegulationSourceDTO> list(
            @Parameter(description = "Regulation namespace to filter to, e.g. \"fda\". Omit for every regulation.")
            @Nullable @QueryValue String regulation,
            HttpRequest<?> httpRequest
    ) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        // Deliberately NOT ApiResponse-wrapped, unlike PUT below — matches
        // StatusController's own "raw read-only list" precedent (see that
        // controller and the CLI's get_status()), kept as-is rather than
        // changed for its own sake.
        return regulation == null ? service.all() : service.forRegulation(regulation);
    }

    @Put
    @Operation(
            summary = "Replace the entire known-source registry",
            description = "No access check — qara_cli_reg_scraper's own push, never reached via auth-gw (see "
                    + "AccessControl). A full replace-in-place sync, not an additive push: every "
                    + "(regulation, source) in the body is upserted, and any existing row NOT in the body is "
                    + "deleted. The body must be the caller's COMPLETE known-source list, never a partial one "
                    + "— see qara_cli_reg_scraper's docs/source-registry-sync.md for when/why this is called "
                    + "(service startup, and once per real `run` invocation)."
    )
    public ApiResponse<List<RegulationSourceDTO>> replace(@Body List<RegulationSourceDTO> sources) {
        return ApiResponse.ok(service.replaceAll(sources));
    }
}
