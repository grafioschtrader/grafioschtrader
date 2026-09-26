package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A currency conversion's mid-rate value in payCurrency, before markup. Independent of tenant currency.")
public record FxMarkupRequest(String payCurrency, String receiveCurrency, Kind kind, double amount,
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date, String mic) {
  public enum Kind {
    TRADE, TRANSFER, INCOME
  }
}
