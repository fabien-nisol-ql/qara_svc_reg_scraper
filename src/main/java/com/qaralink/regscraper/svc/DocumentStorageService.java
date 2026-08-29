package com.qaralink.regscraper.svc;

import com.qaralink.regscraper.exceptions.DocumentContentNotFoundException;
import com.qaralink.regscraper.model.db.ScrapedDocumentEntity;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Reads a scraped document's actual bytes off disk, for GET /v1/documents/{id}/content —
 * everything else in this service only ever handled metadata (see ScrapedDocumentEntity's own
 * javadoc: this Postgres index mirrors qara_cli_reg_scraper's manifest, it doesn't own the
 * content). Local filesystem only for now (see DocumentStorageConfiguration) — reads the whole
 * file into memory, which is fine for the PDF/HTML/XML/JSON documents this scrapes today; revisit
 * with a streamed response if a source ever produces much larger files.
 */
@Singleton
public class DocumentStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentStorageService.class);

    private final Path root;

    public DocumentStorageService(DocumentStorageConfiguration config) {
        this.root = config.getRootPath();
        ensureRootIsWritableByJobContainers();
    }

    /**
     * Confirmed live, same cross-UID gotcha WorkloadConfigService's own comment on config.yaml
     * already documents for a different file: this service runs as root inside its own
     * container, but every spawned qara_cli_reg_scraper job container runs as its own non-root
     * user (uid 1000, "scraper" — see qara_cli_reg_scraper's Dockerfile). A freshly created
     * Docker/Podman named volume is root-owned, mode 700-ish by default — the job's own first
     * write under {@link #root} then fails with a plain PermissionError creating the directory,
     * long before it gets anywhere near an individual document.
     * <p>
     * World-writable (not just world-readable, unlike config.yaml) on purpose: job containers
     * need to CREATE subdirectories here (one per regulation/source/document), not just write a
     * single known file — same "not a secrets file" reasoning applies. Best-effort: a failure
     * here only means the NEXT job's first write may still hit the same PermissionError (logged,
     * not thrown) — this constructor deliberately doesn't fail service startup over it.
     */
    private void ensureRootIsWritableByJobContainers() {
        try {
            Files.createDirectories(root);
            try {
                Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxrwxrwx"));
            } catch (UnsupportedOperationException e) {
                LOG.warn("POSIX permissions not supported on this filesystem — spawned job "
                        + "containers running as a different uid may not be able to write under {}", root);
            }
        } catch (IOException e) {
            LOG.warn("Could not create/chmod the document storage root {} — a spawned job "
                    + "container's first write there may fail with a permission error", root, e);
        }
    }

    public byte[] readContent(ScrapedDocumentEntity entity) throws DocumentContentNotFoundException {
        String storagePath = entity.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            throw new DocumentContentNotFoundException(
                    "Document " + entity.getId() + " (" + qualifiedName(entity) + ") has no storage path recorded"
                            + " — it was never successfully fetched.");
        }

        Path resolved = root.resolve(storagePath).normalize();
        // storagePath always comes from our own DB, never straight from a request, but this
        // costs nothing and a corrupt/hand-edited row (e.g. "../../etc/passwd") should fail
        // loudly rather than read outside the storage root.
        if (!resolved.startsWith(root)) {
            throw new DocumentContentNotFoundException(
                    "Refusing to read a storagePath outside the storage root: " + storagePath);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new DocumentContentNotFoundException(
                    "No file on disk for document " + entity.getId() + " (" + qualifiedName(entity) + ") at "
                            + resolved + " — storage root is " + root
                            + "; is qaralink.storage.local.root pointed at the CLI's actual storage.local.root?");
        }

        try {
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new DocumentContentNotFoundException("Failed to read " + resolved + ": " + e.getMessage());
        }
    }

    /** Best available filename for Content-Disposition — the source's own filename when we
     * captured one (see qara_cli_reg_scraper's original_filename), otherwise a name built from
     * the document id and the storage path's extension. */
    public static String downloadFilename(ScrapedDocumentEntity entity) {
        if (entity.getOriginalFilename() != null && !entity.getOriginalFilename().isBlank()) {
            return entity.getOriginalFilename();
        }

        String extension = "";
        String storagePath = entity.getStoragePath();
        if (storagePath != null) {
            int dot = storagePath.lastIndexOf('.');
            if (dot >= 0) extension = storagePath.substring(dot);
        }
        return entity.getDocumentId().replace('/', '-') + extension;
    }

    private static String qualifiedName(ScrapedDocumentEntity entity) {
        return entity.getRegulation() + ":" + entity.getSource() + ":" + entity.getDocumentId();
    }
}
