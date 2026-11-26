package grafioschtrader.report.pdf;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.reportviews.performance.PeriodStep;
import grafioschtrader.types.PerformanceReportSection;

/**
 * Calendar-year and final-year month strips, directly from one additional yearly performance period. With a benchmark
 * every year and month also shows the benchmark return and the difference in percentage points, and the chart pairs the
 * two returns; a year or month that is not completely covered by the comparison keeps both benchmark cells empty.
 */
@Component
public class AnnualReturnsRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.ANNUAL_RETURNS;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.PORTRAIT);
    if (data.annualPeriod() == null) {
      writer.paragraph(f.text("gt.report.no.holdings"));
      return;
    }
    boolean benchmark = data.annualPeriod().getBenchmark() != null;
    Strip years = new Strip(benchmark, true);
    for (var window : data.annualPeriod().getPeriodWindows()) {
      if (window.periodStepList.stream().noneMatch(PeriodStep.class::isInstance)) {
        continue;
      }
      String year = Integer.toString(window.startDate.getYear());
      LocalDate yearEnd = LocalDate.of(window.startDate.getYear(), 12, 31);
      while (!PerformanceReportPdfService.usable(yearEnd, data.tradingDays())) {
        yearEnd = yearEnd.minusDays(1);
      }
      boolean partial = data.annualPeriod().getFirstDayTotals().getDate().isAfter(window.startDate)
          || (data.tradingDays().firstEverHoldDay != null
              && data.tradingDays().firstEverHoldDay.getYear() == window.startDate.getYear()
              && data.tradingDays().firstEverHoldDay.isAfter(window.startDate))
          || data.request().dateTo.isBefore(yearEnd);
      years.add(f, year, year + (partial ? " *" : ""), window.twrPercent, window.benchmarkTwrPercent,
          window.excessTwrPercent, f.money(window.gainPeriodMC, data.currency()));
    }
    List<PdfTable.Column> yearColumns = new ArrayList<>(
        List.of(PerformanceSummaryRenderer.left(f.text("WM_YEAR"), benchmark ? 1.1f : .8f),
            PerformanceSummaryRenderer.right("TWR %", 1)));
    addBenchmarkColumns(yearColumns, f, benchmark);
    yearColumns.add(PerformanceSummaryRenderer.right(data.currency(), 1.4f));
    paired(writer, f, yearColumns, years);
    writer.paragraph(f.text("gt.report.partial.year"));
    writer.ensureSpace(350);
    writer.heading(f.text("gt.report.month.returns", Integer.toString(data.request().dateTo.getYear())));
    var last = data.annualPeriod().getPeriodWindows().stream()
        .filter(w -> w.startDate.getYear() == data.request().dateTo.getYear()).findFirst();
    Strip months = new Strip(benchmark, false);
    for (int i = 0; i < 12; i++) {
      String month = Month.of(i + 1).getDisplayName(TextStyle.SHORT, f.numberFormat());
      PeriodStep step = last.isPresent() && last.get().periodStepList.get(i) instanceof PeriodStep s ? s : null;
      months.add(f, month, month, step == null ? null : step.twrPercent,
          step == null ? null : step.benchmarkTwrPercent, step == null ? null : step.excessTwrPercent, null);
    }
    List<PdfTable.Column> monthColumns = new ArrayList<>(List.of(
        PerformanceSummaryRenderer.left(f.text("gt.report.month"), 1), PerformanceSummaryRenderer.right("TWR %", 1)));
    addBenchmarkColumns(monthColumns, f, benchmark);
    paired(writer, f, monthColumns, months);
    if (benchmark) {
      writer.paragraph(f.text("gt.report.benchmark.annual.note", data.annualPeriod().getBenchmark().name()));
    }
  }

  private static void addBenchmarkColumns(List<PdfTable.Column> columns, ReportFormatter f, boolean benchmark) {
    if (benchmark) {
      columns.add(PerformanceSummaryRenderer.right(f.text("gt.report.benchmark.column"), 1.3f));
      columns.add(PerformanceSummaryRenderer.right(f.text("gt.report.benchmark.difference.column"), 1));
    }
  }

  private void paired(PdfDocumentWriter writer, ReportFormatter f, List<PdfTable.Column> columns, Strip strip)
      throws IOException {
    writer.ensureSpace(Math.max(245, strip.rows.size() * 22 + 44));
    float top = writer.y();
    float tableWidth = writer.width() * (strip.benchmark ? .56f : .48f);
    new PdfTable(writer, columns, 8, f.text("gt.report.continued"), PdfDocumentWriter.MARGIN, tableWidth)
        .draw(strip.rows);
    PdfChartDrawer.Rect chart = new PdfChartDrawer.Rect(PdfDocumentWriter.MARGIN + tableWidth + 53, top - 200,
        writer.width() - tableWidth - 57, 165);
    if (strip.benchmark) {
      PdfChartDrawer.pairedBarChart(writer, chart, strip.labels, strip.returns, strip.benchmarkReturns,
          List.of("TWR %", f.text("gt.report.benchmark.column")), f::percent);
    } else {
      PdfChartDrawer.barChart(writer, chart, strip.labels, strip.returns, f::percent);
    }
    writer.y(Math.min(writer.y(), top - 230));
  }

  /** Rows, labels and returns of one strip (years or months), with or without the benchmark columns. */
  private static final class Strip {
    final boolean benchmark;
    /** Years show a dash for a missing return, months leave the cell empty. */
    final boolean dashForEmpty;
    final List<PdfTable.Row> rows = new ArrayList<>();
    final List<String> labels = new ArrayList<>();
    final List<Double> returns = new ArrayList<>();
    final List<Double> benchmarkReturns = new ArrayList<>();

    Strip(boolean benchmark, boolean dashForEmpty) {
      this.benchmark = benchmark;
      this.dashForEmpty = dashForEmpty;
    }

    private String cell(ReportFormatter f, Double value) {
      return value == null && !dashForEmpty ? "" : f.percent(value);
    }

    void add(ReportFormatter f, String label, String rowLabel, Double twr, Double benchmarkTwr, Double excess,
        String amount) {
      List<String> cells = new ArrayList<>(List.of(rowLabel, cell(f, twr)));
      if (benchmark) {
        cells.add(cell(f, benchmarkTwr));
        cells.add(excess == null ? cell(f, null) : f.number(excess, 2, 2));
      }
      if (amount != null) {
        cells.add(amount);
      }
      rows.add(new PdfTable.Row(cells));
      labels.add(label);
      returns.add(twr);
      benchmarkReturns.add(benchmarkTwr);
    }
  }
}
