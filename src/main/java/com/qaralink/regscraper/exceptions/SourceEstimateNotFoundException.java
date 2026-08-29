package com.qaralink.regscraper.exceptions;

import com.qaralink.mn.exceptions.HttpExceptionMapping;
import io.micronaut.http.HttpStatus;

@HttpExceptionMapping(status = HttpStatus.NOT_FOUND)
public class SourceEstimateNotFoundException extends Exception {
    public SourceEstimateNotFoundException(String message) {
        super(message);
    }
}
