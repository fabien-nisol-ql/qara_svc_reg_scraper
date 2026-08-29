package com.qaralink.regscraper.svc.workload.k8s;

import lombok.NonNull;

import java.text.Normalizer;
import java.util.Objects;

public class KubernetesUtils {

    private static final int RFC_1123_SUBDOMAIN_MAX_LENGTH = 253;

    private KubernetesUtils() {
    }

    public static String canonicalize(@NonNull String name) {
        return toRfc1123Name(name);
    }

    /**
     * Converts any input into a valid Kubernetes RFC 1123 subdomain name:
     * lowercase, only [a-z0-9-], must start/end with [a-z0-9], max length 253.
     */
    public static String toRfc1123Name(String input) {
        return toRfc1123Name(input, RFC_1123_SUBDOMAIN_MAX_LENGTH);
    }

    public static String toRfc1123Name(String input, int maxLength) {
        Objects.requireNonNull(input, "input must not be null");
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be >= 1");
        }

        String value = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        value = value.toLowerCase();
        value = value.replaceAll("[^a-z0-9-]+", "-");
        value = value.replaceAll("-{2,}", "-");
        value = value.replaceAll("^-+", "").replaceAll("-+$", "");
        if (value.isEmpty()) {
            value = "x";
        }
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
            value = value.replaceAll("-+$", "");
        }
        if (value.isEmpty()) {
            value = "x";
        }
        return value;
    }

    public static boolean isValidRfc1123Name(String value) {
        return value != null
                && value.length() <= RFC_1123_SUBDOMAIN_MAX_LENGTH
                && value.matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$");
    }
}
