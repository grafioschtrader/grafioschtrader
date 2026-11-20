package grafioschtrader.reportviews.securitydividends;

import java.util.List;

import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.DateValue;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.SplitMark;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.YearAmount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Series of the dividend chart for one or several securities. With one security the chart shows its annual dividends,
 * its trailing yield and its splits. With several it compares them by measures without a unit, so that no currency
 * has to be converted: the trailing yield in percent, and the annual dividend as an index that is 100 in a common base
 * year.
 */
@Schema(description = """
    Series of the dividend chart for one or several securities, computed from the shared dividend table. The
    trailing yield and the index are free of a unit, so securities in different currencies can be compared without
    conversion.""")
public record SecurityDividendChart(
    @Schema(description = """
        First completed year in which every compared security already paid dividends for a whole year; the index of
        each security is 100 in this year. Null when there is no such year.""") Integer baseYear,
    @Schema(description = "One series per requested security, in the order of the request") List<Series> series) {

  /**
   * The series of one security.
   *
   * @param idSecuritycurrency the security
   * @param name               name of the security
   * @param currency           currency of the security and of the annual amounts
   * @param years              annual dividends up to the current year
   * @param yieldHistory       trailing yield at each month end
   * @param splits             splits of the security
   * @param index              annual dividend of the completed years from the base year on, relative to the base year
   */
  @Schema(description = "The series of one security")
  public record Series(Integer idSecuritycurrency, String name, String currency, List<YearAmount> years,
      List<DateValue> yieldHistory, List<SplitMark> splits,
      @Schema(description = """
          Annual dividend of the completed years from the base year on, as a percentage of the base year. Empty without
          base year or when the security paid nothing in the base year.""") List<YearValue> index) {
  }

  /** A value of one calendar year. */
  @Schema(description = "A value of one calendar year")
  public record YearValue(int year, double value) {
  }
}
