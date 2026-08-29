package com.qaralink.regscraper.exceptions;

import com.qaralink.mn.exceptions.HttpExceptionMapping;
import io.micronaut.http.HttpStatus;

/** 404 for GET /v1/documents/{id} — no row with that id. */
@HttpExceptionMapping(status = HttpStatus.NOT_FOUND)
public class DocumentNotFoundException extends Exception {
    public DocumentNotFoundException(String message) {
        super(message);
    }
}
