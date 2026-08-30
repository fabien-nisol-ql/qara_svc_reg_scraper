-- Mirrors qara_cli_reg_scraper's RunSummary.stop_reason ("completed" |
-- "budget_reached" | "hard_stop" | "bot_block") - previously computed and
-- written to the local manifest JSON but never actually pushed to this
-- service (ScrapeRunDTO/Manifest._run_dto omitted it entirely). Needed
-- specifically so SourceRetryScheduler can tell "this run stopped because
-- of a detected bot-management block" apart from every other failure
-- kind, and react to it immediately rather than waiting on the generic
-- consecutive-failure threshold - see that scheduler's own updated
-- docstring for why (continuing to automatically retry against a host
-- mid-block plausibly extends the block rather than ever letting it
-- clear - confirmed live, 2026-08-30).
--
-- NULL for any run pushed before this column existed - never backfilled,
-- treated the same as any other non-"bot_block" value everywhere this is
-- read.
ALTER TABLE scrape_run
    ADD COLUMN stop_reason TEXT;
