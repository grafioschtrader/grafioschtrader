package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/** Versioned country rules. Each section evaluates its first matching rule. */
@Schema(description = "Country simulation tax rules with independent trade and income sections.")
public record TaxModelConfig(int version, Section transactionTaxes, Section incomeWithholding) {
  public record Section(List<String> requiredInputs, List<Rule> rules, List<Period> periods) {
  }

  public record Rule(String name, String condition, String expression) {
  }

  public record Period(@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate validFrom,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate validTo, List<Rule> rules) {
  }
}
