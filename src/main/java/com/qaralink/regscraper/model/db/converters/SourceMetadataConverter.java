package com.qaralink.regscraper.model.db.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.qaralink.mn.jpa.converters.JsonAttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/**
 * (De)serializes {@code scraped_document.source_metadata} — an open-ended,
 * source-specific bag of extra fields, mirroring the Python side's
 * {@code source_metadata: dict[str, Any]} column exactly (see
 * qara_cli_reg_scraper's db/models.py).
 */
@Converter
public class SourceMetadataConverter extends JsonAttributeConverter<Map<String, Object>> {
    public SourceMetadataConverter() {
        super(new TypeReference<>() {
        });
    }
}
