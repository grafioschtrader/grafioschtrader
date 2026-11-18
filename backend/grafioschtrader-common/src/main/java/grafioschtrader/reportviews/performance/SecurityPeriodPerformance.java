package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Period performance per security of a client or a portfolio: absolute gain and time-weighted return of every security
    held or traded in the period. Both dates refer to the close of trading; the start date is the excluded base.""")
public record SecurityPeriodPerformance(
    @Schema(description = "Main currency of the figures: the tenant currency or the portfolio currency") String mainCurrency,
    @Schema(description = "Excluded base date of the calculated period") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate dateFrom,
    @Schema(description = "Last date of the calculated period") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate dateTo,
    @Schema(description = """
        One row per security, ordered by gainPeriodMC descending with nulls last, then by name""") List<SecurityPeriodPerformanceRow> rows,
    @Schema(description = "Sums of the main currency amounts over the rows that carry them") Totals totals,
    @Schema(description = "Number of rows without gainPeriodMC; when greater than 0 the totals are a partial total") int omittedRows) {

  @Schema(description = "Sums of the main currency amounts over the rows that carry them, calculated unrounded")
  public record Totals(
      @Schema(description = "Sum of valueStartMC") double valueStartMC,
      @Schema(description = "Sum of valueEndMC") double valueEndMC,
      @Schema(description = "Sum of inflowMC") double inflowMC,
      @Schema(description = "Sum of outflowMC") double outflowMC,
      @Schema(description = "Sum of gainPeriodMC") double gainPeriodMC) {
  }
}
