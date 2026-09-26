package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FX markup in percent with coverage and the selected tariff's provenance. Percent is inapplicable for INVALID.")
public record FxQuote(double percent, FxOutcome outcome, String periodValidFrom, String ruleName,
    FxFeeConfig.Status status, String error) {
}
