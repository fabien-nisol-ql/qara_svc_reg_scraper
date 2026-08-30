-- Alongside V5's next_available_at (the one future instant a source's
-- host reopens), this carries a human-readable description of the
-- RECURRING window itself (e.g. "11:00 PM-5:00 AM America/New York") -
-- qara_cli_reg_scraper's PoliteHttpClient.visiting_hours_description, set
-- by the same estimate() calls, for the same three sources today
-- (clearances_510k/pma/hde). Lets a UI explain WHY a source pauses on a
-- schedule at all, not just when it resumes this one time - see
-- uix_adm_client's regulation-source-card.tsx.
--
-- NULL for the vast majority of sources, same as next_available_at.
ALTER TABLE source_estimate
    ADD COLUMN next_available_note TEXT;
