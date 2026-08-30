-- When automatic suspension began - so a UI can show a human how long a
-- source has been sitting suspended (e.g. "blocked for 47 minutes"),
-- which matters most for the new bot-block-triggered suspension
-- (SourceRetryScheduler suspends immediately on that signal, not after
-- several consecutive failures - see its own docstring) since a human
-- needs to actually decide when it's safe to manually retry, not just be
-- told "it's suspended" with no sense of how stale that state is.
--
-- Set whenever `suspended` transitions false -> true; left untouched on
-- an already-suspended row so the timestamp reflects when the block was
-- FIRST detected, not the last tick that happened to re-confirm it.
-- Cleared back to NULL alongside `suspended`/`suspended_reason` whenever
-- a source recovers (see SourceRetryScheduler.evaluate()'s existing
-- "caught up and healthy again" branch).
ALTER TABLE source_retry_state
    ADD COLUMN suspended_at TIMESTAMPTZ;
