package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.SecurityPositionSeriesRemark;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Series of the security position chart: value, invested capital, results, returns and drawdown of one holding phase
    of one security, in the scope of the view the chart was opened from (tenant, portfolio or security account). The
    arrays are columnar: index i of every array belongs to dates[i].""")
public record SecurityPositionSeriesResult(
    @Schema(description = "The security") Integer idSecuritycurrency,
    @Schema(description = "Name of the security") String securityName,
    @Schema(description = "Currency of the security") String securityCurrency,
    @Schema(description = "Main currency of the scope: tenant currency or portfolio currency") String mainCurrency,
    @Schema(description = "Name of the tenant, portfolio or security account the series are calculated for") String scopeName,
    @Schema(description = """
        True for a CFD or Forex position: invested is the entry value of the open positions and value is entry value
        plus open result, so the percentages are returns on the exposure, not on a deposited margin.""") boolean marginInstrument,
    @Schema(description = "Every holding phase of the security in the scope, newest first") List<Phase> phases,
    @Schema(description = "First day of the phase the series belong to, null without any phase") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate phaseFrom,
    @Schema(description = "Closing day of that phase, null while it is open") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate phaseTo,
    @Schema(description = "Date of the last point of the series, null without any point") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate lastValuedDate,
    @Schema(description = "Notes on missing figures, internal transfers and unsupported instruments") List<SecurityPositionSeriesRemark> remarks,
    @Schema(description = "One date per point in the format yyyy-MM-dd") List<String> dates,
    @Schema(description = "Units held at the close of each point, as traded; 0 on the closing day") List<Double> unitsAsTraded,
    @Schema(description = "Series in the currency of the security") CurrencySeries security,
    @Schema(description = "Series in the main currency, null when it equals the currency of the security") CurrencySeries main,
    @Schema(description = "Purchases, sales, transfers and income of the phase") List<PositionEvent> events) {

  @Schema(description = "One holding phase: from the first day with units after a day without units to the next day without units")
  public record Phase(
      @Schema(description = "First day with units at the close") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate from,
      @Schema(description = "First day without units at the close, null while the phase is open") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate to,
      @Schema(description = "True while the phase is open") boolean open,
      @Schema(description = """
          Realized result plus income of a closed phase in the currency of the security; null for the open phase""") Double totalResultSC) {
  }

  @Schema(description = "One transaction of the phase, shown as a marker")
  public record PositionEvent(
      @Schema(description = "Business date of the transaction") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date,
      @Schema(description = "Type of the transaction") TransactionType transactionType,
      @Schema(description = "Units of the transaction, null for income without units") Double units,
      @Schema(description = """
          Amount in the currency of the security: cost of a purchase, proceeds of a sale, income received""") double amountSC,
      @Schema(description = "True when the transaction is a leg of a security transfer") boolean transfer) {
  }

  @Schema(description = "The series of one currency; every array has one entry per date, null where a figure is missing")
  public record CurrencySeries(
      @Schema(description = "Currency of the series") String currency,
      @Schema(description = "Market value of the units held") List<Double> value,
      @Schema(description = "Cost of the units held, by the cost basis method of each security account") List<Double> invested,
      @Schema(description = "Cost of all purchases of the phase up to the point") List<Double> purchases,
      @Schema(description = "Income of the phase up to the point: dividends, interest") List<Double> income,
      @Schema(description = "Realized result of the sales of the phase up to the point") List<Double> realized,
      @Schema(description = """
          Result of the open position in money: value - invested. Purchases and sales cause no jump, because the money
          put in is taken out.""") List<Double> openResult,
      @Schema(description = """
          Total result since the start of the phase in money: value - invested + income + realized""") List<Double> totalResult,
      @Schema(description = "Return of the open position in percent: (value - invested) / invested") List<Double> openPerformance,
      @Schema(description = """
          Total result since the start of the phase in percent: (value - invested + income + realized) / purchases""") List<Double> totalPerformance,
      @Schema(description = """
          Drawdown of the money in the position in percent: purchases raise the reference high by their amount, sales
          and distributions lower it in proportion""") List<Double> drawdown,
      @Schema(description = "Change of the price since the first point in percent") List<Double> priceChange,
      @Schema(description = "Decline of the price from its highest close within the phase in percent") List<Double> priceDrawdown,
      @Schema(description = "The episode of the deepest drawdown") DrawdownEpisode maxDrawdown,
      @Schema(description = "The longest time below the reference high, null when it is the episode of maxDrawdown") UnderwaterRun longestUnderwater,
      @Schema(description = "Drawdown of the last point in percent; for a closed phase the value at the sale") Double currentDrawdown) {
  }

  @Schema(description = "The episode of the deepest drawdown")
  public record DrawdownEpisode(
      @Schema(description = "Deepest drawdown in percent, null without any valued point") Double depth,
      @Schema(description = "Last point at the reference high before the trough") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate peakDate,
      @Schema(description = "Point of the deepest drawdown") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate troughDate,
      @Schema(description = "First point back at the reference high, null while not regained") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate recoveryDate,
      @Schema(description = "Calendar days from the peak to the recovery or to the last point") Integer durationDays) {
  }

  @Schema(description = "A run below the reference high")
  public record UnderwaterRun(
      @Schema(description = "Last point at the high before the run") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate from,
      @Schema(description = "First point back at the high, or the last point while the run is open") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate to,
      @Schema(description = "Calendar days from the start to the end of the run") int durationDays) {
  }
}
