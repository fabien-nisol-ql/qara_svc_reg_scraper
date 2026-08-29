package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.model.dto.SourceStatusDTO;
import com.qaralink.regscraper.svc.StatusService;
import com.qaralink.regscraper.svc.security.AccessControl;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;

@Controller("/v1/status")
@Tag(name = "Status", description = "Aggregated per-source status — documents count, latest run, latest estimate, all in one row.")
public class StatusController {

    private final StatusService service;
    private final AccessControl accessControl;

    public StatusController(StatusService service, AccessControl accessControl) {
        this.service = service;
        this.accessControl = accessControl;
    }

    @Get
    @Operation(
            summary = "Aggregated status for one or more sources",
            description = "Requires the viewer role WHEN a caller is authenticated at all — see AccessControl; "
                    + "the CLI's own direct, unauthenticated calls (status/reindex) are unaffected by design. "
                    + "\"source\" is a comma-separated list of \"<regulation>:<source>\" qualified names, "
                    + "e.g. fda:ecfr,fda:recalls — same addressing qara_cli_reg_scraper uses. "
                    + "Unlike the CLI, this endpoint doesn't expand \"all\"/\"<regulation>:all\" (it has no "
                    + "registry of what sources exist); pass the exact qualified names you want."
    )
    public List<SourceStatusDTO> status(
            @Parameter(description = "Comma-separated \"<regulation>:<source>\" list, e.g. fda:ecfr,fda:recalls.")
            @QueryValue String source,
            HttpRequest<?> httpRequest
    ) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(qualifiedName -> {
                    String[] parts = qualifiedName.split(":", 2);
                    if (parts.length != 2) {
                        throw new IllegalArgumentException(
                                "\"source\" entries must be \"<regulation>:<source>\" (got " + qualifiedName + ")");
                    }
                    return service.forSource(parts[0], parts[1]);
                })
                .toList();
    }
}
