package com.qaralink.regscraper.svc.security;

import io.micronaut.security.authentication.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caller identity built from the signature-verified {@link Authentication}
 * Micronaut Security resolves from the Bearer JWT auth-gw forwards
 * (validated against Keycloak's JWKS — see application-secure.yaml).
 * Deliberate near-exact copy of {@code QARA_SVC_CMPL}'s own
 * {@code RequestIdentity} — no shared library to depend on instead (see
 * qara_lib_mn issue #8).
 */
public record RequestIdentity(String sub, Set<String> roles, String email, String name, String preferredUsername) {

    public static RequestIdentity from(Authentication authentication, SecurityConfiguration config) {
        Map<String, Object> attributes = authentication.getAttributes();
        return new RequestIdentity(
                authentication.getName(),
                extractRoles(attributes, config.getClientId()),
                stringAttribute(attributes, "email"),
                stringAttribute(attributes, "name"),
                stringAttribute(attributes, "preferred_username"));
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractRoles(Map<String, Object> attributes, String clientId) {
        Object resourceAccessObj = attributes.get("resource_access");
        if (!(resourceAccessObj instanceof Map<?, ?> resourceAccess)) return Collections.emptySet();
        Object clientEntry = resourceAccess.get(clientId);
        if (!(clientEntry instanceof Map<?, ?> clientMap)) return Collections.emptySet();
        Object rolesEntry = clientMap.get("roles");
        if (!(rolesEntry instanceof List<?> rolesList)) return Collections.emptySet();
        return Set.copyOf((List<String>) rolesList);
    }

    private static String stringAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value instanceof String s ? s : null;
    }
}
