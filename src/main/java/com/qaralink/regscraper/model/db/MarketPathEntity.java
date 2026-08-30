package com.qaralink.regscraper.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One regulatory pathway within a {@link MarketEntity} (e.g. FDA's
 * "510K"/"DENOVO"/"PMA"/"HDE"/"EXEMPT", EU's "MDR"/"IVDR"/"EUDAMED") —
 * see {@code V11__add_market.sql}. Moved here from {@code QARA_SVC_CMPL}
 * alongside {@link MarketEntity} — see that class's own docstring.
 */
@Entity
@Table(name = "market_path", uniqueConstraints = @UniqueConstraint(
        name = "uq_market_path_code", columnNames = {"code", "market_code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPathEntity {

    @Id
    private String code;

    @Column(name = "market_code", nullable = false)
    private String marketCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;
}
