-- What the source actually called this document (from Content-Disposition,
-- or the URL's own last path segment) — as distinct from storage_path,
-- which is our own internal "current.<ext>" latest-version convention
-- (see qara_cli_reg_scraper's Manifest module docstring). NULL for rows
-- synced before this column existed, or for a source with no filename-like
-- URL/header to derive one from (e.g. a pure API endpoint).
ALTER TABLE scraped_document
    ADD COLUMN original_filename TEXT;
