package com.qaralink.regscraper.exceptions;

import com.qaralink.mn.exceptions.HttpExceptionMapping;
import io.micronaut.http.HttpStatus;

@HttpExceptionMapping(status = HttpStatus.CONFLICT)
public class ContainerAlreadyRunningException extends RuntimeException {
    public ContainerAlreadyRunningException(String message) {
        super(message);
    }
}
