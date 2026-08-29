package com.qaralink.regscraper.exceptions;

import com.qaralink.mn.exceptions.HttpExceptionMapping;
import io.micronaut.http.HttpStatus;

/**
 * 404 for GET /v1/documents/{id}/content when the row exists but its content isn't reachable:
 * the {@code storagePath} was never set (an "unavailable"/error document that was recorded but
 * never actually fetched), or the file is missing on disk under the configured storage root (a
 * stale/rebuilt index, storage root misconfigured, ...). See DocumentStorageService for which
 * case produced a given message. A missing row itself is DocumentNotFoundException instead —
 * the same failure mode GET /v1/documents/{id} uses.
 */
@HttpExceptionMapping(status = HttpStatus.NOT_FOUND)
public class DocumentContentNotFoundException extends Exception {
    public DocumentContentNotFoundException(String message) {
        super(message);
    }
}
