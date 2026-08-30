-- qara_cli_reg_scraper's PoliteHttpClient now reads a host's own robots.txt
-- Visiting-hours directive (confirmed live on accessdata.fda.gov, the host
-- behind clearances_510k/pma/hde's PDF fetches - see that repo's
-- robots_policy.py) and, when a source's own estimate() checks it, reports
-- the next UTC time that host reopens as PreviewInfo.next_available_at.
-- Manifest.write_estimate pushes it here alongside every other estimate
-- field (PUT /v1/source-estimates/{regulation}/{source}) so SourceRetryScheduler
-- can avoid triggering a job that would immediately no-op against a closed
-- window, and so it's visible to a human via GET /v1/status - see this
-- service's own README for the full design.
--
-- NULL for the vast majority of sources (no such restriction, or currently
-- open either way) - only ever set when a source's host has actually
-- declared a Visiting-hours window AND we're currently outside it.
ALTER TABLE source_estimate
    ADD COLUMN next_available_at TIMESTAMPTZ;
