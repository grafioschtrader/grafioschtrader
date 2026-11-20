package grafioschtrader.reportviews.securitydividends;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dividend key figures of one security, derived from the shared dividend table and not from the distributions a tenant
 * received. Every amount is split-adjusted and converted into the currency of the security with the exchange rate of
 * the ex-date, so that it can be divided by the price of the security. A figure is null when its input is missing:
 * no price, too short a history, a year without exchange rate, or a non-positive base.
 */
@Schema(description = """
    Dividend key figures of one security computed from the shared dividend table, not from the distributions of the
    tenant. Amounts are split-adjusted and converted into the currency of the security with the rate of the ex-date.
    A figure is null when its input is missing. Annual figures use calendar years; the growth rates end with the last
    completed year, so the current year never takes part in them.""")
public record SecurityDividendMetrics(
    @Schema(description = "Currency of the security, which is the currency of every amount") String currency,
    @Schema(description = "Number of dividends with an ex-date up to today") int dividendCount,
    @Schema(description = "Ex-date of the oldest dividend")
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate firstExDate,
    @Schema(description = "Ex-date of the newest dividend up to today")
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate lastExDate,
    @Schema(description = "Amount of the newest dividend up to today") Double lastAmount,
    @Schema(description = "Ex-date of an already known dividend after today, null without one")
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate nextExDate,
    @Schema(description = "Amount of the dividend with the next ex-date") Double nextAmount,
    @Schema(description = "Last price of the security, the base of the yields") Double price,
    @Schema(description = "Sum of the dividends with an ex-date in the last 365 days") Double dividendTTM,
    @Schema(description = "Trailing twelve month dividend yield in percent, the same figure as DIVYIELD")
    Double yieldTTM,
    @Schema(description = """
        Payments per year: the distribution frequency of the security when it names a regular one, otherwise the
        number of dividends in the last 365 days""") Integer paymentsPerYear,
    @Schema(description = "Forward yield in percent: newest dividend times payments per year, divided by the price")
    Double forwardYield,
    @Schema(description = "Growth of the annual dividend over the last completed year, in percent") Double cagr1,
    @Schema(description = "Compound annual growth of the annual dividend over 3 completed years, in percent")
    Double cagr3,
    @Schema(description = "Compound annual growth of the annual dividend over 5 completed years, in percent")
    Double cagr5,
    @Schema(description = "Compound annual growth of the annual dividend over 10 completed years, in percent")
    Double cagr10,
    @Schema(description = """
        Completed years in a row, counted back from the last completed year, whose annual dividend was positive and
        not lower than the year before. Same rule as DIVYEARS, without its cap of 20 years.""")
    Integer yearsWithoutCut,
    @Schema(description = "Newest completed year whose annual dividend was lower than the year before")
    Integer lastCutYear,
    @Schema(description = "Change of the annual dividend in that year, in percent; -100 for a suspended dividend")
    Double lastCutPercent,
    @Schema(description = "Trailing yield plus five year growth rate, the value of the Chowder rule") Double chowder,
    @Schema(description = """
        Trailing dividend divided by the trailing earnings per share of the fundamental data, in percent. Null when no
        selected EPS exists or it is not positive.""") Double payoutRatio,
    @Schema(description = "Annual dividends from the year of the first dividend up to the current year")
    List<YearAmount> years,
    @Schema(description = "Trailing yield at each month end, starting one year after the first dividend")
    List<DateValue> yieldHistory,
    @Schema(description = "Splits of the security, as markers for the chart") List<SplitMark> splits) {

  /**
   * Sum of the dividends of one calendar year.
   *
   * @param year    the calendar year
   * @param amount  the sum, null when a dividend of the year has no exchange rate
   * @param partial true for the current year, which is not completed yet
   * @param cut     true when the sum is lower than the one of the year before
   */
  @Schema(description = "Sum of the dividends of one calendar year")
  public record YearAmount(int year, Double amount, boolean partial, boolean cut) {
  }

  /** One point of a time series. */
  @Schema(description = "One point of a time series")
  public record DateValue(@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date, double value) {
  }

  /** One split, which divides the old share into toFactor / fromFactor new shares. */
  @Schema(description = "One split of the security")
  public record SplitMark(@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date, int fromFactor,
      int toFactor) {
  }

  /** Metrics of a security without any dividend. */
  public static SecurityDividendMetrics empty(String currency, Double price) {
    return new SecurityDividendMetrics(currency, 0, null, null, null, null, null, price, null, null, null, null, null,
        null, null, null, null, null, null, null, null, List.of(), List.of(), List.of());
  }
}
