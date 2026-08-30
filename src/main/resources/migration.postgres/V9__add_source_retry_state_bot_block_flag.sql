-- Distinguishes a bot-block-triggered suspension (immediate, on the very
-- first occurrence - see SourceRetryScheduler's own javadoc) from the
-- generic consecutive-failures one, WITHOUT parsing suspended_reason's
-- free text to tell them apart - an explicit typed field, matching every
-- other boolean-flag decision in this table. Read by ScrapeJobService.trigger
-- to enforce qaralink.scheduler.bot-block-cooldown-hours before even a
-- MANUAL retry is allowed for this specific suspension kind (see
-- BotBlockCooldownActiveException) - the generic suspension kind has no
-- such cooldown, a human clicking "Update now" always clears it
-- immediately, same as before this column existed.
ALTER TABLE source_retry_state
    ADD COLUMN suspended_due_to_bot_block BOOLEAN NOT NULL DEFAULT false;
