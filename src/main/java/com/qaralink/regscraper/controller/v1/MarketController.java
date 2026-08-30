package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.model.dto.MarketDTO;
import com.qaralink.regscraper.svc.MarketService;
import com.qaralink.regscraper.svc.security.AccessControl;
import com.qaralink.rest.ApiResponse;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;

/**
 * Market management, moved here from {@code QARA_SVC_CMPL} (2026-08-30)
 * — see {@code MarketEntity}'s own docstring for why. Same endpoint
 * shape as that service's own (now-superseded, for the admin UI's
 * purposes) {@code MarketController}, minus its journey-derived {@code
 * /v1/markets/stats} — that's product/journey analytics, not market
 * *management*, and stays in {@code QARA_SVC_CMPL} where the underlying
 * journey/product data actually lives.
 */
@Controller("/v1/markets")
@Tag(name = "Markets", description = "Markets (regulation namespaces) and their regulatory pathways.")
public class MarketController {

    private final MarketService marketService;
    private final AccessControl accessControl;

    public MarketController(MarketService marketService, AccessControl accessControl) {
        this.marketService = marketService;
        this.accessControl = accessControl;
    }

    @Get
    @Operation(summary = "List every known market", description = "Requires the viewer role when authenticated "
            + "(see AccessControl) — no JWT at all (the CLI's own direct traffic) is let through unchecked.")
    public Page<MarketDTO> list(Pageable pageable, HttpRequest<?> request) {
        accessControl.requireRoleIfAuthenticated(request, accessControl.viewerRole());
        return marketService.list(pageable);
    }

    @Get("/{code}")
    @Operation(summary = "One market by code", description = "Requires the viewer role when authenticated.")
    public HttpResponse<ApiResponse<MarketDTO>> getByCode(
            @PathVariable @NotBlank String code, HttpRequest<?> request
    ) {
        accessControl.requireRoleIfAuthenticated(request, accessControl.viewerRole());
        return HttpResponse.ok(
                ApiResponse.ok(
                        marketService.findByCode(code)
                                .orElseThrow(() -> new EntityNotFoundException("there is no market with code " + code))));
    }
}
