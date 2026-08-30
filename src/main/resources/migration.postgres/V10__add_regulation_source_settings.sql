-- Each source's EFFECTIVE scraping settings (SourceSettings override if
-- set in qara_cli_reg_scraper's own config.yaml, else the matching
-- global default) - pushed by the same PUT /v1/sources sync that already
-- keeps label/description current (see RegulationSourceService#replaceAll
-- and that repo's docs/source-registry-sync.md). Previously none of this
-- was visible anywhere outside that CLI's own config.yaml.
--
-- recheck_after_days/lookback_days being NULL is a real, meaningful value
-- (never re-checked; not a lookback-windowed source), not "not synced
-- yet" - all five columns are nullable only because a row synced by an
-- older CLI version (before this column existed) won't have sent them.
ALTER TABLE regulation_source
    ADD COLUMN enabled                   BOOLEAN,
    ADD COLUMN requests_per_second       DOUBLE PRECISION,
    ADD COLUMN max_new_documents_per_run INTEGER,
    ADD COLUMN recheck_after_days        INTEGER,
    ADD COLUMN lookback_days             INTEGER;
