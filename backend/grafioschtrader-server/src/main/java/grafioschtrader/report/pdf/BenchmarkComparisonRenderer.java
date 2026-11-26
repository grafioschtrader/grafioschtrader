package grafioschtrader.report.pdf;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.reportviews.performance.BenchmarkChartPoint;
import grafioschtrader.reportviews.performance.BenchmarkMetrics;
import grafioschtrader.reportviews.performance.BenchmarkNotice;
import grafioschtrader.reportviews.performance.PerformanceBenchmarkResult;
import grafioschtrader.reportviews.performance.ShadowPortfolioResult;
import grafioschtrader.types.PerformanceReportSection;

/**
 * Comparison of the reporting period with its benchmark: which benchmark was compared, where it came from, what its
 * return contains and over which dates both sides were compared, the two returns and their difference, the notices of
 * the comparison and a chart of both cumulative returns. The service leaves the section out when no benchmark resolved,
 * so a resolved benchmark without usable data still shows its identity and the reason.
 */
@Component
public class BenchmarkComparisonRenderer implements SectionRenderer {

  /** Prefix of the MessageFormat texts of the notices; the notice code in lower case completes the key. */
  static final String NOTICE_KEY_PREFIX = "gt.report.benchmark.notice.";

  public PerformanceReportSection section() {
    return PerformanceReportSection.BENCHMARK_COMPARISON;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.PORTRAIT);
    PerformanceBenchmarkResult benchmark = data.period().getBenchmark();
    writer.labelValueRow(f.text("benchmark.name"), benchmark.name());
    writer.labelValueRow(f.text("BENCHMARK_SOURCE"), f.text(benchmark.source().name()));
    writer.labelValueRow(f.text("RETURN_BASIS"), f.text(benchmark.returnBasis().name()));
    if (benchmark.fxPair() != null) {
      writer.labelValueRow(f.text("FX_PAIR"), benchmark.fxPair());
    }
    writer.labelValueRow(f.text("EFFECTIVE_FROM"), date(f, benchmark.effectiveFrom()));
    writer.labelValueRow(f.text("EFFECTIVE_TO"), date(f, benchmark.effectiveTo()));
    BenchmarkMetrics m = benchmark.metrics();
    if (m != null) {
      writer.heading(f.text("PERFORMANCE_RETURN"));
      writer.labelValueRow(f.text("PORTFOLIO_TWR_PERCENT"), f.percent(m.portfolioTwrPercent()));
      writer.labelValueRow(f.text("BENCHMARK_TWR_PERCENT"), f.percent(m.benchmarkTwrPercent()));
      writer.labelValueRow(f.text("EXCESS_RETURN_PERCENT"), points(f, m.excessReturnPercent()));
      writer.labelValueRow(f.text("PORTFOLIO_TWR_ANNUALIZED_PERCENT"), f.percent(m.portfolioTwrAnnualizedPercent()));
      writer.labelValueRow(f.text("BENCHMARK_TWR_ANNUALIZED_PERCENT"), f.percent(m.benchmarkTwrAnnualizedPercent()));
      writer.labelValueRow(f.text("EXCESS_RETURN_ANNUALIZED_PERCENT"), points(f, m.excessReturnAnnualizedPercent()));
      writer.labelValueRow(f.text("INCLUDED_INTERVALS"), f.number((double) m.includedIntervals(), 0, 0));
      renderRisk(writer, f, m);
    }
    renderShadow(writer, f, benchmark.shadow(), benchmark.mainCurrency());
    for (BenchmarkNotice notice : benchmark.notices()) {
      writer.paragraph(noticeText(f, notice));
    }
    if (m != null && !benchmark.chart().isEmpty()) {
      List<BenchmarkChartPoint> points = benchmark.chart();
      writer.heading(f.text("gt.report.benchmark.chart"));
      PdfChartDrawer.lineChart(writer, PerformanceChartsRenderer.rectangle(writer),
          points.stream().map(BenchmarkChartPoint::date).toList(),
          List.of(
              new PdfChartDrawer.Series(f.text("PORTFOLIO_TWR_PERCENT"),
                  points.stream().map(BenchmarkChartPoint::portfolioTwrPercent).toList(), false, null),
              new PdfChartDrawer.Series(f.text("BENCHMARK_TWR_PERCENT"),
                  points.stream().map(BenchmarkChartPoint::benchmarkTwrPercent).toList(), true, null)),
          f::percent, f);
    }
    writer.paragraph(f.text("gt.report.benchmark.method"));
  }

  /** The relative risk; the figures below the minimum number of observations are shown as a dash. */
  private static void renderRisk(PdfDocumentWriter writer, ReportFormatter f, BenchmarkMetrics m) throws IOException {
    writer.heading(f.text("PERFORMANCE_BENCHMARK_RISK"));
    writer.labelValueRow(f.text("RISK_OBSERVATIONS"), f.number((double) m.riskObservations(), 0, 0));
    writer.labelValueRow(f.text("TRACKING_ERROR_PERCENT"), f.percent(m.trackingErrorPercent()));
    writer.labelValueRow(f.text("INFORMATION_RATIO"), ratio(f, m.informationRatio()));
    writer.labelValueRow(f.text("BENCHMARK_BETA"), ratio(f, m.beta()));
    writer.labelValueRow(f.text("BENCHMARK_CORRELATION"), ratio(f, m.correlation()));
    writer.labelValueRow(f.text("JENSEN_ALPHA_ANNUALIZED_PERCENT"), f.percent(m.jensenAlphaAnnualizedPercent()));
    writer.labelValueRow(f.text("UP_CAPTURE_PERCENT"), f.percent(m.upCapturePercent()));
    writer.labelValueRow(f.text("DOWN_CAPTURE_PERCENT"), f.percent(m.downCapturePercent()));
    writer.labelValueRow(f.text("BENCHMARK_VOLATILITY_ANNUALIZED_PERCENT"),
        f.percent(m.benchmarkVolatilityAnnualizedPercent()));
    writer.labelValueRow(f.text("BENCHMARK_MAX_DRAWDOWN_PERCENT"), f.percent(m.benchmarkMaxDrawdownPercent()));
    writer.paragraph(f.text("gt.report.benchmark.risk.method"));
  }

  private static String ratio(ReportFormatter f, Double value) {
    return value == null ? "–" : f.number(value, 2, 2);
  }

  /**
   * The figures of the shadow portfolio. After a shortfall only the start value and the date of the shortfall are
   * shown; the notice explains it.
   */
  private static void renderShadow(PdfDocumentWriter writer, ReportFormatter f, ShadowPortfolioResult shadow,
      String currency) throws IOException {
    if (shadow == null) {
      return;
    }
    writer.heading(f.text("PERFORMANCE_SHADOW"));
    writer.labelValueRow(f.text("SHADOW_VALUE_START_MC"), money(f, currency, shadow.valueStartMC()));
    if (shadow.shortfallDate() != null) {
      writer.labelValueRow(f.text("SHADOW_SHORTFALL_DATE"), date(f, shadow.shortfallDate()));
    } else {
      writer.labelValueRow(f.text("SHADOW_VALUE_END_MC"), money(f, currency, shadow.valueEndMC()));
      writer.labelValueRow(f.text("SHADOW_GAIN_MC"), money(f, currency, shadow.gainMC()));
      writer.labelValueRow(f.text("SHADOW_DIFFERENCE_MC"), money(f, currency, shadow.differenceMC()));
      writer.labelValueRow(f.text("MWR_PERCENT"), f.percent(shadow.mwrPercent()));
      writer.labelValueRow(f.text("MWR_ANNUALIZED_PERCENT"), f.percent(shadow.mwrAnnualizedPercent()));
    }
    writer.paragraph(f.text("gt.report.benchmark.shadow.method"));
  }

  private static String money(ReportFormatter f, String currency, Double value) {
    return value == null ? "–" : f.money(value, currency);
  }

  /**
   * The text of a notice: its parameters fill the placeholders in the order the server put them in, dates formatted
   * for the reader and a leg translated, since its value is an NLS key.
   */
  static String noticeText(ReportFormatter f, BenchmarkNotice notice) {
    Object[] args = notice.parameters().entrySet().stream().map(e -> parameter(f, e)).toArray();
    return f.text(NOTICE_KEY_PREFIX + notice.code().name().toLowerCase(), args);
  }

  private static String parameter(ReportFormatter f, Map.Entry<String, String> entry) {
    if (entry.getKey().equals("leg")) {
      return f.text(entry.getValue());
    }
    return entry.getKey().toLowerCase().endsWith("date") ? f.date(LocalDate.parse(entry.getValue()))
        : entry.getValue();
  }

  private static String date(ReportFormatter f, LocalDate date) {
    return date == null ? "–" : f.date(date);
  }

  /** A difference of two returns is in percentage points, so it carries no percent sign. */
  private static String points(ReportFormatter f, Double value) {
    return value == null ? "–" : f.number(value, 2, 2);
  }
}
