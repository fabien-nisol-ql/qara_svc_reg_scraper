-- Market management, moved here from QARA_SVC_CMPL (that service's own
-- `market`/`market_path` tables are untouched and still exist there —
-- its own journeys/statistics still reference them locally via real FK
-- constraints, so this is a deliberate duplication, not a migration
-- with a cutover: this service becomes the new source of truth for
-- what the admin UI shows, CMPL's own copy keeps serving its own
-- journeys/stats independently.
--
-- Same shape as CMPL's own market/market_path tables (see that repo's
-- V1__init.sql) so uix_adm_client's existing Market/MarketPath types
-- (code/name/description, Market.paths: MarketPath[]) need no changes
-- at all - only which service's GET /v1/markets it calls.
CREATE TABLE market
(
    code        VARCHAR(50) PRIMARY KEY, -- e.g. FDA, EU, CA
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL
);

CREATE TABLE market_path
(
    code        VARCHAR(100) PRIMARY KEY,
    market_code VARCHAR(50)  NOT NULL REFERENCES market (code) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    CONSTRAINT uq_market_path_code UNIQUE (code, market_code)
);

CREATE INDEX idx_market_path_market_code ON market_path (market_code);

-- =========================================================
-- Seed FDA/EU (copied verbatim from QARA_SVC_CMPL's own V1__init.sql)
-- plus CA, added here for the first time.
-- =========================================================
INSERT INTO market (code, name, description)
VALUES ('FDA', 'United States (FDA)', 'U.S. market regulated by the Food and Drug Administration for medical devices.'),
       ('EU', 'Europe (EU)',
        'European market for medical devices and IVDs, including MDR/IVDR compliance and related registrations.'),
       ('CA', 'Canada (Health Canada)',
        'Canadian market regulated by Health Canada / Santé Canada for medical devices, under the Medical Devices Regulations (SOR/98-282).');

INSERT INTO market_path (market_code, code, name, description)
VALUES ('FDA', '510K', '510(k) Premarket Notification',
        'Substantial equivalence pathway for many medical devices.'),
       ('FDA', 'DENOVO', 'De Novo Classification Request',
        'Pathway for novel devices with no predicate, low-to-moderate risk.'),
       ('FDA', 'PMA', 'Premarket Approval (PMA)',
        'Pathway for Class III devices requiring scientific evidence of safety/effectiveness.'),
       ('FDA', 'HDE', 'Humanitarian Device Exemption (HDE)',
        'Pathway for devices intended to benefit patients with rare conditions.'),
       ('FDA', 'EXEMPT', 'Exempt',
        'Device types exempt from 510(k), subject to applicable controls and requirements.'),
       ('EU', 'MDR', 'EU MDR', 'Medical Device Regulation (EU) 2017/745 compliance pathway.'),
       ('EU', 'IVDR', 'EU IVDR',
        'In Vitro Diagnostic Medical Device Regulation (EU) 2017/746 compliance pathway.'),
       ('EU', 'EUDAMED', 'EUDAMED Registration',
        'Registration and data submission activities in the European database on medical devices.');

-- No CA market_path rows seeded on purpose: unlike FDA/EU above (copied
-- from CMPL's own existing, already-decided taxonomy), Canada's
-- regulatory pathway codes/names haven't been decided anywhere - add
-- them here once they are, rather than guessing.
