package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/** Currency-rounded estimate and country matches with structured diagnostics. */
@Schema(description = "Tax estimate with coverage and structured, nonfatal diagnostics.")
public record TaxEstimateResult(double estimatedTax, String currency, boolean complete, List<Match> matchedRules,
    List<Warning> warnings) {
  public record Match(String country, String section,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate validFrom,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate validTo, String rule, double amount) {
  }

  public record Warning(String code, String country, String section, String detail) {
  }
}
