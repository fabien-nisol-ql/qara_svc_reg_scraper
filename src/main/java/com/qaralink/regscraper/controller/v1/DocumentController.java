package com.qaralink.regscraper.controller.v1;

import com.qaralink.regscraper.exceptions.DocumentContentNotFoundException;
import com.qaralink.regscraper.exceptions.DocumentNotFoundException;
import com.qaralink.regscraper.model.db.ScrapedDocumentEntity;
import com.qaralink.regscraper.model.dto.ScrapedDocumentDTO;
import com.qaralink.regscraper.svc.DocumentStorageService;
import com.qaralink.regscraper.svc.ScrapedDocumentService;
import com.qaralink.regscraper.svc.security.AccessControl;
import com.qaralink.rest.ApiResponse;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

@Controller("/v1/documents")
@Tag(name = "Documents", description = "Scraped document records — what qara_cli_reg_scraper's manifest holds, upserted here.")
public class DocumentController {

    private final ScrapedDocumentService service;
    private final DocumentStorageService storage;
    private final AccessControl accessControl;

    public DocumentController(ScrapedDocumentService service, DocumentStorageService storage, AccessControl accessControl) {
        this.service = service;
        this.storage = storage;
        this.accessControl = accessControl;
    }

    @Post
    @Operation(summary = "Upsert a scraped document", description = "Creates or updates the row for (regulation, source, documentId). "
            + "No access check — qara_cli_reg_scraper's own push, never reached via auth-gw (see AccessControl).")
    public ApiResponse<ScrapedDocumentDTO> upsert(@Body ScrapedDocumentDTO document) {
        return ApiResponse.ok(service.upsert(document));
    }

    @Get
    @Operation(
            summary = "List/search scraped documents",
            description = "Requires the viewer role when authenticated (see AccessControl). Filterable by "
                    + "regulation and/or source, plus an optional free-text \"q\" matched against "
                    + "title/documentId/originalFilename; paginated."
    )
    public Page<ScrapedDocumentDTO> search(
            @Parameter(description = "Regulation namespace, e.g. \"fda\".") @Nullable @QueryValue String regulation,
            @Parameter(description = "Source name within the regulation, e.g. \"ecfr\". Requires regulation to also be set.")
            @Nullable @QueryValue String source,
            @Parameter(description = "Free-text filter over title/documentId/originalFilename.")
            @Nullable @QueryValue String q,
            Pageable pageable,
            HttpRequest<?> httpRequest
    ) {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        return service.search(regulation, source, q, pageable);
    }

    @Get("/{id}")
    @Operation(summary = "One document's metadata by row id", description = "Requires the viewer role when "
            + "authenticated (see AccessControl). \"id\" is the row id from search's results, not documentId.")
    public ApiResponse<ScrapedDocumentDTO> get(@PathVariable Long id, HttpRequest<?> httpRequest) throws DocumentNotFoundException {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        return service.get(id)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new DocumentNotFoundException("No document found with id " + id));
    }

    @Get("/{id}/content")
    @Operation(
            summary = "The document's actual content",
            description = "Requires the viewer role when authenticated (see AccessControl). Streams the fetched "
                    + "file itself (PDF/HTML/XML/JSON/...), not metadata — content-type and filename come from "
                    + "what was recorded when it was fetched. \"id\" is the row id from search's results, not "
                    + "documentId (which can itself contain '/'). Served inline by default (suitable for an "
                    + "<iframe>/<embed> preview); pass download=true for an attachment Content-Disposition "
                    + "instead. Local storage backend only for now — see DocumentStorageService."
    )
    public HttpResponse<byte[]> content(
            @PathVariable Long id,
            @Parameter(description = "attachment instead of inline Content-Disposition.")
            @QueryValue(defaultValue = "false") boolean download,
            HttpRequest<?> httpRequest
    ) throws DocumentNotFoundException, DocumentContentNotFoundException {
        accessControl.requireRoleIfAuthenticated(httpRequest, accessControl.viewerRole());
        ScrapedDocumentEntity entity = service.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException("No document found with id " + id));
        byte[] content = storage.readContent(entity);
        String filename = DocumentStorageService.downloadFilename(entity).replace("\"", "'");
        String contentType = entity.getContentType() != null ? entity.getContentType() : MediaType.APPLICATION_OCTET_STREAM;

        return HttpResponse.ok(content)
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (download ? "attachment" : "inline") + "; filename=\"" + filename + "\"");
    }
}
