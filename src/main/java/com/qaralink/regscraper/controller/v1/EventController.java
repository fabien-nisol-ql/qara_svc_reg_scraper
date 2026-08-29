package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.model.dto.ScrapeEventDTO;
import com.qaralink.regscraper.svc.ScrapeEventService;
import com.qaralink.regscraper.svc.security.AccessControl;
import com.qaralink.rest.ApiResponse;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

@Controller("/v1/events")
@Tag(name = "Events", description = "Full per-document event history (new/updated/unchanged/error), one row per event.")
public class EventController {

    private final ScrapeEventService service;
    private final AccessControl accessControl;

    public EventController(ScrapeEventService service, AccessControl accessControl) {
        this.service = service;
        this.accessControl = accessControl;
    }

    @Post
    @Operation(summary = "Record one scrape event", description = "Always an insert — every event, including "
            + "repeated \"unchanged\" checks, is its own row. No access check — qara_cli_reg_scraper's own push, "
            + "never reached via auth-gw (see AccessControl).")
    public ApiResponse<ScrapeEventDTO> record(@Body ScrapeEventDTO event) {
        return ApiResponse.ok(service.record(event));
    }

    @Get
    @Operation(summary = "List events for a source, or a specific run",
            description = "Requires the viewer role when authenticated (see AccessControl). `event` (e.g. "
                    + "\"error\") further filters a `runId` lookup — the front end's \"see the error details\" "
                    + "button uses this to fetch just the error rows for the run behind a source's \"completed "
                    + "with N errors\" note, without paging through every unchanged/new event alongside them.")
    public Page<ScrapeEventDTO> search(
            @Nullable @QueryValue String regulation,
            @Nullable @QueryValue String source,
            @Nullable @QueryValue String runId,
            @Nullable @QueryValue String event,
            Pageable pageable,
            HttpRequest<?> httpRequest
    ) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        if (runId != null) {
            return service.byRun(runId, event, pageable);
        }
        return service.search(regulation, source, pageable);
    }
}
