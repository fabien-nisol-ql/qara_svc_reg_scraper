package com.qaralink.regscraper.model.dto;

import com.qaralink.regscraper.model.db.MarketPathEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One regulatory pathway within a market, e.g. FDA's 510(k) or EU's MDR.")
public class MarketPathDTO {

    @Schema(example = "510K", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "Short display name.", example = "510(k) Premarket Notification")
    private String name;

    @Schema(description = "What this pathway covers.")
    private String description;

    public static MarketPathDTO from(MarketPathEntity e) {
        return MarketPathDTO.builder()
                .code(e.getCode())
                .name(e.getName())
                .description(e.getDescription())
                .build();
    }
}
