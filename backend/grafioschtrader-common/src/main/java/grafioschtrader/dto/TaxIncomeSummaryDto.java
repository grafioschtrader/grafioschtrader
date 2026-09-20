package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

/** Persistent income reconciliation and grouped diagnostics of a simulation run. */
public record TaxIncomeSummaryDto(int version, List<WarningGroup> warnings, List<IncomeTotals> income) {
  public record WarningGroup(String code, String country, String section, Integer security, Integer account, long count,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate firstDate,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate lastDate, String eventIdentity,
      String detail) {
  }

  public record IncomeTotals(String kind, String currency, double grossPaid, double withholdingPaid, double netPaid,
      double grossReceivables, double estimatedWithholding, double netReceivables, double reconciliation) {
  }
}
