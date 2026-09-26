package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Unsaved fee editor document and conversion to preview. Supply the account or plan identifier for its endpoint.")
public record FxMarkupPreviewRequest(Integer idSecurityaccount, Integer idTradingPlatformPlan, String yaml,
    FxMarkupRequest request) {
}
