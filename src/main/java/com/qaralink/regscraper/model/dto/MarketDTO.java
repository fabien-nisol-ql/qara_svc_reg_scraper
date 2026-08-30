package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.MarketEntity;
import com.qaralink.regscraper.model.db.MarketPathEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A market and its regulatory pathways — {@code GET /v1/markets} /
 * {@code GET /v1/markets/{code}}. Field names/shape match {@code
 * QARA_SVC_CMPL}'s own (now-superseded, for the admin UI's purposes)
 * {@code MarketDTO} exactly, since {@code uix_adm_client}'s existing
 * {@code Market}/{@code MarketPath} types are unchanged by this move —
 * see {@code MarketEntity}'s own docstring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A market (regulation namespace) and its regulatory pathways.")
public class MarketDTO {

    @Schema(example = "FDA", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "Short display name.", example = "United States (FDA)")
    private String name;

    @Schema(description = "What this market covers.")
    private String description;

    private List<MarketPathDTO> paths;

    public static MarketDTO from(MarketEntity market, List<MarketPathEntity> paths) {
        return MarketDTO.builder()
                .code(market.getCode())
                .name(market.getName())
                .description(market.getDescription())
                .paths(paths.stream().map(MarketPathDTO::from).toList())
                .build();
    }
}
