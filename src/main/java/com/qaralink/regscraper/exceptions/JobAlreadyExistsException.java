package com.qaralink.regscraper.exceptions;

import com.qaralink.mn.exceptions.HttpExceptionMapping;
import io.micronaut.http.HttpStatus;

@HttpExceptionMapping(status = HttpStatus.CONFLICT)
public class JobAlreadyExistsException extends RuntimeException {
    public JobAlreadyExistsException(String message) {
        super(message);
    }
}
