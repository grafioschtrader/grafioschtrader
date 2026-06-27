package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.types.SeasonalPeriodType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Result of a seasonality heat map calculation for a single security or currency pair. Holds a matrix of period
    returns (rows = years, columns = months or quarters) expressed as percentages, a trailing annual-return column,
    and per-column footer statistics. Capability flags tell the UI which of the dividend and currency toggles are
    applicable for the analysed instrument.""")
public class SeasonalReturnsResult {

  @Schema(description = "Granularity of the period columns: MONTHLY (12 columns) or QUARTERLY (4 columns)")
  public final SeasonalPeriodType periodType;

  @Schema(description = "ISO currency code the returns are expressed in (instrument currency or tenant main currency)")
  public final String currency;

  @Schema(description = "True when dividends/interest were included in the returns")
  public final boolean dividendsIncluded;

  @Schema(description = "True when the returns were converted into the tenant's main currency")
  public final boolean inTenantCurrency;

  @Schema(description = """
      True when the instrument has at least one dividend/interest record, i.e. the include-dividends toggle is
      meaningful. False for instruments that never pay a distribution (the UI disables the toggle).""")
  public final boolean dividendsAvailable;

  @Schema(description = """
      True when the instrument currency differs from the tenant main currency and a conversion is therefore possible.
      Always false for currency pairs and when both currencies are identical (the UI disables the toggle).""")
  public final boolean currencyConversionAvailable;

  @Schema(description = "One row per calendar year, ordered descending (most recent first)")
  public final List<SeasonalYearRow> yearRows = new ArrayList<>();

  @Schema(description = """
      Footer statistics, one entry per period column in column order, followed by one final entry for the annual
      column.""")
  public final List<SeasonalColumnStat> columnStats = new ArrayList<>();

  public SeasonalReturnsResult(SeasonalPeriodType periodType, String currency, boolean dividendsIncluded,
      boolean inTenantCurrency, boolean dividendsAvailable, boolean currencyConversionAvailable) {
    this.periodType = periodType;
    this.currency = currency;
    this.dividendsIncluded = dividendsIncluded;
    this.inTenantCurrency = inTenantCurrency;
    this.dividendsAvailable = dividendsAvailable;
    this.currencyConversionAvailable = currencyConversionAvailable;
  }

  @Schema(description = "Period returns of a single calendar year")
  public static class SeasonalYearRow {
    @Schema(description = "The calendar year")
    public final int year;

    @Schema(description = """
        Period returns in percent, in column order (12 entries for monthly, 4 for quarterly). An entry is null when no
        return could be computed for that period (e.g. no preceding close or the period lies outside the price
        history).""")
    public final List<Double> periodReturns;

    @Schema(description = "Full-year return in percent, or null when it cannot be computed")
    public final Double annualReturn;

    @Schema(description = """
        True when this year does not contain all period columns (typically the first and last year of the available
        history), so the UI can mark it as partial.""")
    public final boolean partial;

    public SeasonalYearRow(int year, List<Double> periodReturns, Double annualReturn, boolean partial) {
      this.year = year;
      this.periodReturns = periodReturns;
      this.annualReturn = annualReturn;
      this.partial = partial;
    }
  }

  @Schema(description = "Aggregated statistics for one matrix column across all years")
  public static class SeasonalColumnStat {
    @Schema(description = "Mean return of the column in percent")
    public final Double mean;

    @Schema(description = "Median return of the column in percent")
    public final Double median;

    @Schema(description = "Standard deviation of the column returns in percent")
    public final Double stdDev;

    @Schema(description = "Share of years with a positive return in this column, in percent (hit rate)")
    public final Double pctPositive;

    @Schema(description = "Number of years contributing a value to this column")
    public final int count;

    public SeasonalColumnStat(Double mean, Double median, Double stdDev, Double pctPositive, int count) {
      this.mean = mean;
      this.median = median;
      this.stdDev = stdDev;
      this.pctPositive = pctPositive;
      this.count = count;
    }
  }
}
