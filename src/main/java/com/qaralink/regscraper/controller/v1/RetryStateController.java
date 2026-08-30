package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.model.dto.RegulationSourceDTO;
import com.qaralink.regscraper.model.dto.RetryStateResponseDTO;
import com.qaralink.regscraper.svc.RegulationSourceService;
import com.qaralink.regscraper.svc.SourceRetryStateService;
import com.qaralink.regscraper.svc.security.AccessControl;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

import java.util.List;

@Controller("/v1/retry-state")
@Tag(
        name = "Retry State",
        description = "Automatic-retry policy + per-source circuit-breaker state — see "
                + "SourceRetryScheduler. One bulk read (like GET /v1/sources, GET /v1/status), "
                + "not per-source, including the configured policy values so a UI's explainer "
                + "stays accurate without hardcoding them."
)
public class RetryStateController {

    private final RegulationSourceService sourceService;
    private final SourceRetryStateService retryStateService;
    private final AccessControl accessControl;
    private final int retryIntervalMinutes;
    private final int steadyStateIntervalMinutes;
    private final int maxConsecutiveFailures;
    private final int botBlockCooldownHours;

    public RetryStateController(
            RegulationSourceService sourceService,
            SourceRetryStateService retryStateService,
            AccessControl accessControl,
            @Value("${qaralink.scheduler.retry-interval-minutes}") int retryIntervalMinutes,
            @Value("${qaralink.scheduler.steady-state-check-interval-minutes}") int steadyStateIntervalMinutes,
            @Value("${qaralink.scheduler.retry-max-consecutive-failures}") int maxConsecutiveFailures,
            @Value("${qaralink.scheduler.bot-block-cooldown-hours}") int botBlockCooldownHours
    ) {
        this.sourceService = sourceService;
        this.retryStateService = retryStateService;
        this.accessControl = accessControl;
        this.retryIntervalMinutes = retryIntervalMinutes;
        this.steadyStateIntervalMinutes = steadyStateIntervalMinutes;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.botBlockCooldownHours = botBlockCooldownHours;
    }

    @Get
    @Operation(summary = "Retry policy + per-source state", description = "Requires the viewer role when "
            + "authenticated (see AccessControl). Optionally filtered to one regulation, e.g. \"fda\".")
    public RetryStateResponseDTO get(
            @Parameter(description = "Regulation namespace to filter to, e.g. \"fda\". Omit for every regulation.")
            @Nullable @QueryValue String regulation,
            HttpRequest<?> httpRequest
    ) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        List<RegulationSourceDTO> knownSources = regulation == null
                ? sourceService.all()
                : sourceService.forRegulation(regulation);
        return RetryStateResponseDTO.builder()
                .retryIntervalMinutes(retryIntervalMinutes)
                .steadyStateIntervalMinutes(steadyStateIntervalMinutes)
                .maxConsecutiveFailures(maxConsecutiveFailures)
                .botBlockCooldownHours(botBlockCooldownHours)
                .sources(retryStateService.allFor(knownSources))
                .build();
    }
}
