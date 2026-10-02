package grafioschtrader.report.pdf;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import grafioschtrader.common.ReturnSeries;
import grafioschtrader.reportviews.performance.PeriodStep;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;

/** Vector views of the exact retained return series, never of the screen's cash-plus-securities delta line. */
@Component
public class PerformanceChartsRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.PERFORMANCE_CHARTS;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.PORTRAIT);
    ReturnSeries series = data.period().getReturnSeries();
    if (series == null || data.period().getMetrics().twrPercent() == null) {
      writer.paragraph(f.text("gt.report.no.twr"));
    } else {
      List<LocalDate> dates = IntStream.range(0, series.size()).mapToObj(series::date).toList();
      List<Boolean> regular = IntStream.range(0, series.size()).mapToObj(series::isRegular).toList();
      PdfChartDrawer.lineChart(writer, rectangle(writer), dates,
          List.of(new PdfChartDrawer.Series(f.text("gt.report.cumulative.twr"),
              IntStream.range(0, series.size()).mapToObj(i -> (series.wealth(i) - 1) * 100).toList(), false, regular)),
          f::percent, f);
    }
    // The short report keeps to two pages; every other selection shows the value and window charts as well.
    if (data.request().preset == PerformanceReportPreset.SHORT_REPORT || series == null) {
      return;
    }
    List<LocalDate> dates = IntStream.range(0, series.size()).mapToObj(series::date).toList();
    List<Boolean> regular = IntStream.range(0, series.size()).mapToObj(series::isRegular).toList();
    var flows = data.period().getExternalCashTransfers();
    PdfChartDrawer
        .lineChart(
            writer, rectangle(writer), dates, List
                .of(new PdfChartDrawer.Series(f.text("gt.report.value"),
                    IntStream.range(0, series.size()).mapToObj(series::value).toList(), false, regular),
                    new PdfChartDrawer.Series(f.text("gt.report.start.flows"),
                        IntStream.range(0, series.size())
                            .mapToObj(i -> series.value(0) + flows.get(i) - flows.getFirst()).toList(),
                        true, regular)),
            v -> f.money(v, data.currency()), f);
    List<String> labels = new ArrayList<>();
    List<Double> values = new ArrayList<>();
    var windows = data.period().getPeriodWindows().stream()
        .filter(w -> w.periodStepList.stream().anyMatch(PeriodStep.class::isInstance)).toList();
    if (data.period().getPeriodSplit() == WeekYear.WM_YEAR && windows.size() == 1) {
      for (int i = 0; i < 12; i++) {
        labels.add(Integer.toString(i + 1));
        values.add(windows.getFirst().periodStepList.get(i) instanceof PeriodStep step ? step.twrPercent : null);
      }
    } else {
      int previousMonth = -1;
      for (var window : windows) {
        labels.add(windows.size() > 24
            ? (window.startDate.getMonthValue() != previousMonth
                ? window.startDate.format(DateTimeFormatter.ofPattern("MMM", f.numberFormat()))
                : "")
            : data.period().getPeriodSplit() == WeekYear.WM_YEAR ? Integer.toString(window.startDate.getYear())
                : f.date(window.startDate));
        values.add(window.twrPercent);
        previousMonth = window.startDate.getMonthValue();
      }
    }
    writer.ensureSpace(265);
    writer.heading(f.text("gt.report.window.twr"));
    PdfChartDrawer.barChart(writer, rectangle(writer), labels, values, f::percent);
    writer.paragraph(f.text("gt.report.chart.gaps"));
  }

  static PdfChartDrawer.Rect rectangle(PdfDocumentWriter writer) throws IOException {
    writer.ensureSpace(224);
    float bottom = writer.y() - 190;
    writer.y(bottom - 30);
    return new PdfChartDrawer.Rect(PdfDocumentWriter.MARGIN + 80, bottom, writer.width() - 87, 150);
  }
}
