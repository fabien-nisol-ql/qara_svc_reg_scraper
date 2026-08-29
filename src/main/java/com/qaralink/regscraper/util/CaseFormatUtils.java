package com.qaralink.regscraper.util;

import io.micronaut.core.naming.NameUtils;

import java.util.Objects;

public final class CaseFormatUtils {

    private CaseFormatUtils() {
    }

    public static String toKebabCase(String value) {
        require(value);
        return NameUtils.hyphenate(value, false);
    }

    private static void require(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
