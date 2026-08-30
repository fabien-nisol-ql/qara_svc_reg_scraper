package com.qaralink.regscraper.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A market qara_cli_reg_scraper scrapes for ({@code code} is the same
 * regulation namespace string used everywhere else in this service —
 * "fda", "eu", "ca" — uppercased here to match the existing convention
 * this table was copied from). Moved here from {@code QARA_SVC_CMPL}
 * (2026-08-30): that service's own {@code market}/{@code market_path}
 * tables are untouched and still exist there — its journeys/statistics
 * still reference them locally via real FK constraints, so this is a
 * deliberate duplication, not a migration with a cutover. This service
 * is now the source of truth for what the admin UI shows.
 * <p>
 * A market's paths ({@link MarketPathEntity}) are a separate, flat
 * entity keyed by {@code marketCode} — no JPA {@code @OneToMany}
 * relationship mapping, matching every other entity in this codebase
 * (see {@link RegulationSourceEntity}): {@link
 * com.qaralink.regscraper.svc.MarketService} assembles the two with a
 * second query rather than an eager object graph.
 */
@Entity
@Table(name = "market")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketEntity {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;
}
