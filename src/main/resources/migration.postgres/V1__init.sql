-- Mirrors qara_cli_reg_scraper's own (soon-to-be-retired) SQLAlchemy schema
-- (src/qara_reg_scraper/db/models.py) 1:1 for the first four tables, plus
-- two new job-tracking tables this service owns that the CLI never had.

CREATE TABLE scraped_document
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regulation       TEXT        NOT NULL,
    source           TEXT        NOT NULL,
    document_id      TEXT        NOT NULL,
    title            TEXT,
    canonical_url    TEXT,
    storage_path     TEXT,
    content_hash     TEXT,
    content_type     TEXT,
    size_bytes       BIGINT,
    version_count    INTEGER     NOT NULL DEFAULT 1,
    first_seen_at    TIMESTAMPTZ,
    last_scraped_at  TIMESTAMPTZ,
    last_checked_at  TIMESTAMPTZ,
    last_changed_at  TIMESTAMPTZ,
    source_metadata  TEXT        NOT NULL DEFAULT '{}',
    CONSTRAINT uq_regulation_source_document UNIQUE (regulation, source, document_id)
);

CREATE INDEX ix_scraped_document_regulation ON scraped_document (regulation);
CREATE INDEX ix_scraped_document_source ON scraped_document (source);

CREATE TABLE scrape_run
(
    run_id         TEXT PRIMARY KEY,
    regulation     TEXT        NOT NULL,
    source         TEXT        NOT NULL,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    status         TEXT        NOT NULL,
    checked        INTEGER     NOT NULL DEFAULT 0,
    new            INTEGER     NOT NULL DEFAULT 0,
    updated        INTEGER     NOT NULL DEFAULT 0,
    unchanged      INTEGER     NOT NULL DEFAULT 0,
    errors         INTEGER     NOT NULL DEFAULT 0,
    error_details  TEXT        NOT NULL DEFAULT '[]'
);

CREATE INDEX ix_scrape_run_regulation ON scrape_run (regulation);
CREATE INDEX ix_scrape_run_source ON scrape_run (source);

CREATE TABLE scrape_event
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id        TEXT REFERENCES scrape_run (run_id),
    regulation    TEXT        NOT NULL,
    source        TEXT        NOT NULL,
    document_id   TEXT        NOT NULL,
    event         TEXT        NOT NULL,
    ts            TIMESTAMPTZ,
    url           TEXT,
    http_status   INTEGER,
    content_hash  TEXT,
    storage_path  TEXT,
    error         TEXT
);

CREATE INDEX ix_scrape_event_run_id ON scrape_event (run_id);
CREATE INDEX ix_scrape_event_regulation_source_document ON scrape_event (regulation, source, document_id);

-- Latest known "what's left to do" snapshot per (regulation, source) —
-- upserted in place on every real run, not historical (unlike the tables
-- above). Absent entirely for a source no run has ever computed one for.
CREATE TABLE source_estimate
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regulation       TEXT        NOT NULL,
    source           TEXT        NOT NULL,
    computed_at      TIMESTAMPTZ,
    total_available  INTEGER,
    already_known    INTEGER,
    remaining        INTEGER,
    note             TEXT,
    CONSTRAINT uq_regulation_source_estimate UNIQUE (regulation, source)
);

-- One row per triggered scrape job (manual REST call or the scheduler),
-- tracking its Docker/Kubernetes execution — new to this service, the CLI
-- itself has no equivalent (it only knows about its own single run).
CREATE TABLE scrape_job
(
    job_id              TEXT PRIMARY KEY,
    sources             TEXT        NOT NULL, -- JSON array of "regulation:source" strings
    status              TEXT        NOT NULL,
    provider            TEXT        NOT NULL, -- "docker" | "kubernetes"
    triggered_by        TEXT        NOT NULL, -- "manual" | "scheduler"
    submitted_at        TIMESTAMPTZ NOT NULL,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    exit_code           INTEGER,
    display_message     TEXT,
    diagnostic_message  TEXT
);

CREATE INDEX ix_scrape_job_status ON scrape_job (status);
CREATE INDEX ix_scrape_job_submitted_at ON scrape_job (submitted_at);

CREATE TABLE scrape_job_history
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id              TEXT        NOT NULL REFERENCES scrape_job (job_id),
    status              TEXT        NOT NULL,
    changed_at          TIMESTAMPTZ NOT NULL,
    display_message     TEXT,
    diagnostic_message  TEXT
);

CREATE INDEX ix_scrape_job_history_job_id ON scrape_job_history (job_id);
