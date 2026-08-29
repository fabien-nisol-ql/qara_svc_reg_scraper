package com.qaralink.regscraper.svc.security;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

/**
 * Mirrors {@code QARA_SVC_CMPL}'s own {@code SecurityConfiguration} —
 * the platform has no shared authorization library to depend on instead
 * (confirmed live: {@code qara_lib_mn} carries no security code at all;
 * see qara_lib_mn issue #8, filed to track centralizing this eventually).
 * Role names are configurable, defaulting to ADR-005's own canonical
 * lowercase Keycloak client-role strings — "admin"/"viewer", never the
 * display name "Administrator".
 */
@ConfigurationProperties("qaralink.security")
@Data
public class SecurityConfiguration {
    private String clientId = "qaralink-platform";
    private String adminRole = "admin";
    private String viewerRole = "viewer";
}
