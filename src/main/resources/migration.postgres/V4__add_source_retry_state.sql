-- Owned entirely by the new SourceRetryScheduler (unlike regulation_source/
-- source_estimate, nothing in qara_cli_reg_scraper ever pushes a row here) -
-- one row per (regulation, source) tracking the automatic hourly-retry
-- circuit breaker: how many consecutive attempts have failed, when the next
-- automatic retry is due, and whether auto-retry has given up and flagged
-- the source for engineering review. See that scheduler's own docstring for
-- the full state machine, and qara_cli_reg_scraper's
-- docs/retry-and-backlog-catchup.md for the CLI-side half of this feature.
CREATE TABLE source_retry_state
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regulation            TEXT        NOT NULL,
    source                TEXT        NOT NULL,
    consecutive_failures  INTEGER     NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMPTZ,
    suspended             BOOLEAN     NOT NULL DEFAULT false,
    suspended_reason      TEXT,
    -- The last scrape_job.job_id this state has already reacted to - the
    -- scheduler ticks every qaralink.scheduler.retry-check-cadence-minutes
    -- (default 1) but a source's own retry cadence is much coarser
    -- (retry-interval-minutes, default 60), so this stops the same
    -- terminal job outcome from incrementing/resetting consecutive_failures
    -- on every tick between actual retries.
    last_evaluated_job_id TEXT,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_source_retry_state UNIQUE (regulation, source)
);
