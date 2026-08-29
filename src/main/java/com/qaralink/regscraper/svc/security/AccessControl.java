package com.qaralink.regscraper.svc.security;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * The one piece neither {@code OPC_SVC_ACCNT} nor {@code QARA_SVC_CMPL}
 * needs: this service's REST API is reached by two genuinely different
 * callers on the SAME endpoints — the browser via auth-gw (a real,
 * validated JWT always present) and qara_cli_reg_scraper itself, which
 * calls this service directly on the internal docker network and never
 * carries a JWT at all (confirmed: {@code QARA_REG_SCRAPER_SERVICE__BASE_URL}
 * points straight at this service, bypassing auth-gw entirely). Gating an
 * endpoint the "mandatory auth" way both siblings use would 401 every one
 * of the CLI's own calls.
 * <p>
 * So: no JWT at all is treated as "this is the trusted internal CLI" and
 * let through with no role check; a JWT that IS present but lacks the
 * required role gets a real 403. See this service's own README, "Access
 * control" section, for exactly which endpoints use which method below —
 * and qara_lib_mn issue #8 for the follow-up to centralize this (and
 * {@link RequestIdentity}/{@link SecurityConfiguration}) alongside the
 * other services that separately reimplement the same shape.
 * <p>
 * Centralized here as one injectable bean, rather than a private
 * per-controller helper repeated in every controller (the pattern
 * {@code QARA_SVC_CMPL} uses) — purely a local cleanup, same underlying
 * {@code Authentication}-from-request-attribute mechanics.
 */
@Singleton
public class AccessControl {

    private final SecurityConfiguration config;

    public AccessControl(SecurityConfiguration config) {
        this.config = config;
    }

    public String adminRole() {
        return config.getAdminRole();
    }

    public String viewerRole() {
        return config.getViewerRole();
    }

    /**
     * No JWT at all → 401 (this endpoint has no legitimate anonymous
     * caller — unlike {@link #requireRoleIfAuthenticated}, nothing should
     * ever reach it without a real, role-bearing session). JWT present
     * but lacking {@code role} → 403.
     */
    public void requireRole(HttpRequest<?> request, String role) {
        RequestIdentity identity = identity(request)
                .orElseThrow(() -> new HttpStatusException(
                        HttpStatus.UNAUTHORIZED, "No authenticated principal — Bearer token required"));
        if (!identity.hasRole(role)) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Requires the \"" + role + "\" role");
        }
    }

    /**
     * No JWT at all → allowed through, no check at all (the CLI's own
     * direct, unauthenticated traffic — see this class's own docstring).
     * JWT present but lacking {@code role} → 403.
     */
    public void requireRoleIfAuthenticated(HttpRequest<?> request, String role) {
        identity(request).ifPresent(identity -> {
            if (!identity.hasRole(role)) {
                throw new HttpStatusException(HttpStatus.FORBIDDEN, "Requires the \"" + role + "\" role");
            }
        });
    }

    private Optional<RequestIdentity> identity(HttpRequest<?> request) {
        return request.getAttribute(SecurityFilter.AUTHENTICATION)
                .map(o -> (Authentication) o)
                .map(authentication -> RequestIdentity.from(authentication, config));
    }
}
