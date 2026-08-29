-- Replaces the hand-maintained Java list (RegulationSourceRegistry.java) that
-- used to back GET /v1/sources with a real table qara_cli_reg_scraper keeps in
-- sync itself (PUT /v1/sources, a full replace-in-place of every known
-- (regulation, source) pair) - see that repo's docs/source-registry-sync.md
-- for exactly when that sync runs. synced_at is server-assigned on every
-- upsert (not client-supplied), so it doubles as "how stale is this row" -
-- notably NOT NULL, unlike scraped_document's timestamps, since every row
-- here (including the seed rows below) has one from the moment it exists.
CREATE TABLE regulation_source
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regulation  TEXT        NOT NULL,
    source      TEXT        NOT NULL,
    label       TEXT,
    description TEXT,
    synced_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_regulation_source UNIQUE (regulation, source)
);

-- Seed with what RegulationSourceRegistry.java already had (plus the two
-- sources it had already drifted out of sync on - classification, fdc_act) so
-- GET /v1/sources isn't empty on a fresh deploy before the first real sync
-- lands. synced_at here is just "migration time", not a real sync - the next
-- actual sync (service startup, or any real scrape run) overwrites it.
INSERT INTO regulation_source (regulation, source, label, description, synced_at)
VALUES
    ('fda', 'ecfr', 'eCFR', '21 CFR medical device regulations (eCFR versioner API)', now()),
    ('fda', 'guidance', 'Guidance Documents', 'FDA guidance documents for medical devices', now()),
    ('fda', 'clearances_510k', '510(k) Clearances', 'FDA 510(k) / De Novo device clearances, plus their summary documents', now()),
    ('fda', 'warning_letters', 'Warning Letters', 'FDA warning letters, all centers', now()),
    ('fda', 'recalls', 'Recalls', 'FDA medical device recalls / enforcement reports', now()),
    ('fda', 'classification', 'Product Classification', 'FDA device product classification (product code -> device class/regulation)', now()),
    ('fda', 'fdc_act', 'FD&C Act', 'Federal Food, Drug, and Cosmetic Act, full text', now());
