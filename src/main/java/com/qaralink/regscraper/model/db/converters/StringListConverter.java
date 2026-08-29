package com.qaralink.regscraper.model.db.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.qaralink.mn.jpa.converters.JsonAttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * (De)serializes {@code scrape_job.sources} — the list of
 * "regulation:source" qualified names a job was triggered for.
 */
@Converter
public class StringListConverter extends JsonAttributeConverter<List<String>> {
    public StringListConverter() {
        super(new TypeReference<>() {
        });
    }
}
