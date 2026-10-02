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

/** Flat version of the screen treetable, including all slots, step returns and the grand total. */
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
    List<PdfTable.Column> columns = new ArrayList<>();
    columns.add(PerformanceSummaryRenderer.left(f.text(year ? "WM_YEAR" : "WM_WEEK"), year ? 1.4f : 2));
    for (int i = 1; i <= count; i++) {
      columns.add(PerformanceSummaryRenderer.right(year ? Month.of(i).getDisplayName(TextStyle.SHORT, f.numberFormat())
          : DayOfWeek.of(i).getDisplayName(TextStyle.SHORT, f.numberFormat()), 1));
    }
    columns.add(PerformanceSummaryRenderer.right(f.text("TOTAL_GAIN"), 1.4f));
    columns.add(PerformanceSummaryRenderer.right("TWR %", 1.1f));
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
    }
    List<String> totals = new ArrayList<>();
    totals.add(f.text("GRAND_TOTAL"));
    for (double value : data.period().getSumPeriodColSteps()) {
      totals.add(f.money(value, data.currency()));
    }
    totals.add(f.money(data.period().getDifference().getTotalGainMC(), data.currency()));
    totals.add(f.percent(data.period().getMetrics() == null ? null : data.period().getMetrics().twrPercent()));
    rows.add(new PdfTable.Row(totals, PdfTable.Kind.TOTAL));
    new PdfTable(writer, columns, year ? 7 : 8, f.text("gt.report.continued")).draw(rows);
    writer.paragraph(f.text("gt.report.window.note", data.currency()));
  }

  private String marker(PeriodStepMissingHoliday slot) {
    return switch (slot.holidayMissing) {
    case HM_HOLIDAY -> "H";
    case HM_HISTORY_DATA_MISSING -> "M";
    default -> "";
    };
  }
}
