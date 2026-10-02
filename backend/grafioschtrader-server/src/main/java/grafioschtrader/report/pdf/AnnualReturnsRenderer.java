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

/** Calendar-year and final-year month strips, directly from one additional yearly performance period. */
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
    List<PdfTable.Row> rows = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    List<Double> values = new ArrayList<>();
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
      rows.add(new PdfTable.Row(List.of(year + (partial ? " *" : ""), f.percent(window.twrPercent),
          f.money(window.gainPeriodMC, data.currency()))));
      labels.add(year);
      values.add(window.twrPercent);
    }
    paired(
        writer, f, List.of(PerformanceSummaryRenderer.left(f.text("WM_YEAR"), .8f),
            PerformanceSummaryRenderer.right("TWR %", 1), PerformanceSummaryRenderer.right(data.currency(), 1.4f)),
        rows, labels, values);
    writer.paragraph(f.text("gt.report.partial.year"));
    writer.ensureSpace(350);
    writer.heading(f.text("gt.report.month.returns", Integer.toString(data.request().dateTo.getYear())));
    var last = data.annualPeriod().getPeriodWindows().stream()
        .filter(w -> w.startDate.getYear() == data.request().dateTo.getYear()).findFirst();
    rows = new ArrayList<>();
    labels = new ArrayList<>();
    values = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      String month = Month.of(i + 1).getDisplayName(TextStyle.SHORT, f.numberFormat());
      Double value = last.isPresent() && last.get().periodStepList.get(i) instanceof PeriodStep step ? step.twrPercent
          : null;
      rows.add(new PdfTable.Row(List.of(month, value == null ? "" : f.percent(value))));
      labels.add(month);
      values.add(value);
    }
    paired(writer, f, List.of(PerformanceSummaryRenderer.left(f.text("gt.report.month"), 1),
        PerformanceSummaryRenderer.right("TWR %", 1)), rows, labels, values);
  }

  private void paired(PdfDocumentWriter writer, ReportFormatter f, List<PdfTable.Column> columns,
      List<PdfTable.Row> rows, List<String> labels, List<Double> values) throws IOException {
    writer.ensureSpace(Math.max(245, rows.size() * 22 + 44));
    float top = writer.y();
    float tableWidth = writer.width() * .48f;
    new PdfTable(writer, columns, 8, f.text("gt.report.continued"), PdfDocumentWriter.MARGIN, tableWidth).draw(rows);
    PdfChartDrawer.barChart(writer, new PdfChartDrawer.Rect(PdfDocumentWriter.MARGIN + tableWidth + 53, top - 200,
        writer.width() - tableWidth - 57, 165), labels, values, f::percent);
    writer.y(Math.min(writer.y(), top - 230));
  }
}
