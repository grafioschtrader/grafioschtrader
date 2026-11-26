package grafioschtrader.report.pdf;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.reportviews.performance.PeriodStep;
import grafioschtrader.reportviews.performance.PeriodStepMissingHoliday;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.types.PerformanceReportSection;

/**
 * Flat version of the screen treetable, including all slots, step returns and the grand total. With a benchmark every
 * window gets two further rows, as on the screen: the benchmark return and the difference in percentage points, per
 * slot and for the window in the return column. A slot or window not completely covered by the comparison keeps its
 * cells empty, and the grand total shows them only when the comparison spans the whole period. The benchmark is shown in
 * rows rather than in further columns, because further columns would narrow the amount columns until a large amount
 * no longer fits.
 */
@Component
public class PeriodWindowsRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.PERIOD_WINDOWS;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    boolean year = data.period().getPeriodSplit() == WeekYear.WM_YEAR;
    writer.beginSection(section(),
        year ? PdfDocumentWriter.Orientation.LANDSCAPE : PdfDocumentWriter.Orientation.PORTRAIT);
    if (data.period().getMetrics() == null) {
      writer.paragraph(f.text("gt.report.no.holdings"));
      return;
    }
    int count = year ? 12 : 5;
    var benchmark = data.period().getBenchmark();
    List<PdfTable.Column> columns = new ArrayList<>();
    columns.add(PerformanceSummaryRenderer.left(f.text(year ? "WM_YEAR" : "WM_WEEK"), year ? 1.4f : 2));
    for (int i = 1; i <= count; i++) {
      columns.add(PerformanceSummaryRenderer.right(year ? Month.of(i).getDisplayName(TextStyle.SHORT, f.numberFormat())
          : DayOfWeek.of(i).getDisplayName(TextStyle.SHORT, f.numberFormat()), 1));
    }
    columns.add(PerformanceSummaryRenderer.right(f.text("TOTAL_GAIN"), 1.4f));
    columns.add(PerformanceSummaryRenderer.right("TWR %", 1.1f));
    String benchmarkLabel = "   " + f.text("gt.report.benchmark.column") + " %";
    String differenceLabel = "   " + f.text("gt.report.benchmark.difference.column");
    List<PdfTable.Row> rows = new ArrayList<>();
    for (var window : data.period().getPeriodWindows()) {
      List<String> amounts = new ArrayList<>();
      List<String> returns = new ArrayList<>();
      amounts.add(year ? Integer.toString(window.startDate.getYear()) : f.date(window.startDate));
      returns.add("   TWR %");
      for (var slot : window.periodStepList) {
        amounts.add(slot instanceof PeriodStep step ? f.money(step.gainMC + step.marginCloseGainMC, data.currency())
            : marker(slot));
        returns.add(slot instanceof PeriodStep step ? f.percent(step.twrPercent) : "");
      }
      amounts.add(f.money(window.gainPeriodMC, data.currency()));
      amounts.add(f.percent(window.twrPercent));
      returns.add("");
      returns.add("");
      rows.add(new PdfTable.Row(amounts));
      rows.add(new PdfTable.Row(returns));
      if (benchmark != null) {
        List<String> benchmarkRow = new ArrayList<>(List.of(benchmarkLabel));
        List<String> differenceRow = new ArrayList<>(List.of(differenceLabel));
        for (var slot : window.periodStepList) {
          PeriodStep step = slot instanceof PeriodStep s ? s : null;
          benchmarkRow.add(step == null ? "" : percentOrEmpty(f, step.benchmarkTwrPercent));
          differenceRow.add(step == null ? "" : pointsOrEmpty(f, step.excessTwrPercent));
        }
        benchmarkRow.addAll(List.of("", percentOrEmpty(f, window.benchmarkTwrPercent)));
        differenceRow.addAll(List.of("", pointsOrEmpty(f, window.excessTwrPercent)));
        rows.add(new PdfTable.Row(benchmarkRow));
        rows.add(new PdfTable.Row(differenceRow));
      }
    }
    List<String> totals = new ArrayList<>();
    totals.add(f.text("GRAND_TOTAL"));
    for (double value : data.period().getSumPeriodColSteps()) {
      totals.add(f.money(value, data.currency()));
    }
    totals.add(f.money(data.period().getDifference().getTotalGainMC(), data.currency()));
    totals.add(f.percent(data.period().getMetrics() == null ? null : data.period().getMetrics().twrPercent()));
    rows.add(new PdfTable.Row(totals, PdfTable.Kind.TOTAL));
    if (benchmark != null) {
      boolean wholePeriod = benchmark.metrics() != null && benchmark.requestedFrom().equals(benchmark.effectiveFrom())
          && benchmark.requestedTo().equals(benchmark.effectiveTo());
      rows.add(totalComparisonRow(benchmarkLabel, count,
          wholePeriod ? percentOrEmpty(f, benchmark.metrics().benchmarkTwrPercent()) : ""));
      rows.add(totalComparisonRow(differenceLabel, count,
          wholePeriod ? pointsOrEmpty(f, benchmark.metrics().excessReturnPercent()) : ""));
    }
    new PdfTable(writer, columns, year ? 7 : 8, f.text("gt.report.continued")).draw(rows);
    writer.paragraph(f.text("gt.report.window.note", data.currency()));
  }

  /** A row under the grand total with only its label and the value in the return column. */
  private static PdfTable.Row totalComparisonRow(String label, int slots, String value) {
    List<String> cells = new ArrayList<>(List.of(label));
    for (int i = 0; i < slots; i++) {
      cells.add("");
    }
    cells.addAll(List.of("", value));
    return new PdfTable.Row(cells, PdfTable.Kind.TOTAL);
  }

  private static String percentOrEmpty(ReportFormatter f, Double value) {
    return value == null ? "" : f.percent(value);
  }

  /** A difference of two returns is in percentage points, so it carries no percent sign. */
  private static String pointsOrEmpty(ReportFormatter f, Double value) {
    return value == null ? "" : f.number(value, 2, 2);
  }

  private String marker(PeriodStepMissingHoliday slot) {
    return switch (slot.holidayMissing) {
    case HM_HOLIDAY -> "H";
    case HM_HISTORY_DATA_MISSING -> "M";
    default -> "";
    };
  }
}
