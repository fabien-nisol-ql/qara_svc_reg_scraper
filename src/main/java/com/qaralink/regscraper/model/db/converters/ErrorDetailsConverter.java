package com.qaralink.regscraper.model.db.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.qaralink.mn.jpa.converters.JsonAttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;
import java.util.Map;

/**
 * (De)serializes {@code scrape_run.error_details} — a list of
 * {@code {document_id, error}} maps, mirroring the Python side's
 * {@code error_details: list[dict[str, str]]} field exactly.
 */
@Converter
public class ErrorDetailsConverter extends JsonAttributeConverter<List<Map<String, String>>> {
    public ErrorDetailsConverter() {
        super(new TypeReference<>() {
        });
    }
}
