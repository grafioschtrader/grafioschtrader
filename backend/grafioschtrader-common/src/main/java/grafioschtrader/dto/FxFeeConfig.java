package grafioschtrader.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Percentage currency conversion tariff, independent of commission and custody periods.")
public record FxFeeConfig(String amountCurrency, Map<String, List<String>> currencyClasses, List<FeeRule> rules,
    List<Period> periods) {
  public enum Status {
    VERIFIED, ASSUMED
  }

  @Schema(description = "Inclusive dated FX tariff with evidence and an explicit historical assumption.")
  public record Period(String validFrom, String validTo, Status status, String source, String note,
      List<FeeRule> rules) {
  }
}
