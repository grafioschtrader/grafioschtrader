package grafioschtrader.report.pdf;

import java.time.ZonedDateTime;
import java.util.List;

import grafioschtrader.dto.PerformanceReportRequest;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.PerformancePeriod;

/** All data loaded before rendering starts; renderers have no repository or request dependencies. */
public record ReportData(ReportContext context, PerformanceReportRequest request, String scope, String currency,
    List<String> portfolios, boolean excludeDivTax, boolean feeInterestFxAtCutOffDate, ZonedDateTime created,
    FirstAndMissingTradingDays tradingDays, PerformancePeriod period, PerformancePeriod annualPeriod,
    HoldingsData holdings, HoldingsData openingHoldings, BookingData bookings) {
  public ReportData {
    portfolios = List.copyOf(portfolios);
  }

  public String title(ReportFormatter formatter) {
    return request.title == null || request.title.isBlank()
        ? formatter.text(request.hasPeriod() ? "gt.report.performance.title" : "gt.report.statement.title")
        : request.title;
  }
}
