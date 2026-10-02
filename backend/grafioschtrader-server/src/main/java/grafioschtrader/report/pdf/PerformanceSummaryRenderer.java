package grafioschtrader.report.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import org.springframework.stereotype.Component;

import grafioschtrader.reportviews.performance.PeriodHoldingAndDiff;
import grafioschtrader.types.PerformanceReportSection;

/** The screen's asset bridge and twelve from/to/difference figures, with their original meanings. */
@Component
public class PerformanceSummaryRenderer implements SectionRenderer {
  private record Figure(String key, ToDoubleFunction<PeriodHoldingAndDiff> value) {
  }

  private static final List<Figure> FIGURES = List.of(
      new Figure("DIVIDEND_REAL", PeriodHoldingAndDiff::getDividendRealMC),
      new Figure("FEE_REAL", PeriodHoldingAndDiff::getFeeRealMC),
      new Figure("INTEREST_CASHACCOUNT_REAL", PeriodHoldingAndDiff::getInterestCashaccountRealMC),
      new Figure("EXTERNAL_CASH_TRANSFER", PeriodHoldingAndDiff::getExternalCashTransferMC),
      new Figure("ACCUMULATE_REDUCE", PeriodHoldingAndDiff::getAccumulateReduceMC),
      new Figure("SECURITIES_AND_MARGIN_GAIN", PeriodHoldingAndDiff::getSecuritiesAndMarginGainMC),
      new Figure("security.risk", PeriodHoldingAndDiff::getSecurityRiskMC),
      new Figure("CASH_BALANCE", PeriodHoldingAndDiff::getCashBalanceMC),
      new Figure("TOTAL_BALANCE", PeriodHoldingAndDiff::getTotalBalanceMC),
      new Figure("GAIN", PeriodHoldingAndDiff::getGainMC),
      new Figure("MARGIN_CLOSE_GAIN", PeriodHoldingAndDiff::getMarginCloseGainMC),
      new Figure("TOTAL_GAIN", PeriodHoldingAndDiff::getTotalGainMC));

  public PerformanceReportSection section() {
    return PerformanceReportSection.PERFORMANCE_SUMMARY;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.PORTRAIT);
    var period = data.period();
    if (period.getMetrics() == null) {
      writer.paragraph(f.text("gt.report.no.holdings"));
      return;
    }
    List<PdfTable.Row> bridge = List.of(
        row(f.text("gt.report.start.value"), f.money(period.getFirstDayTotals().getTotalBalanceMC(), data.currency())),
        row(f.text("gt.report.net.flows"),
            f.money(period.getDifference().getExternalCashTransferMC(), data.currency())),
        row(f.text("gt.report.result"), f.money(period.getDifference().getTotalGainMC(), data.currency())),
        new PdfTable.Row(List.of(f.text("gt.report.end.value"),
            f.money(period.getLastDayTotals().getTotalBalanceMC(), data.currency())), PdfTable.Kind.TOTAL));
    new PdfTable(writer, List.of(left(f.text("gt.report.bridge"), 3), right(data.currency(), 2)), 10,
        f.text("gt.report.continued")).draw(bridge);
    var m = period.getMetrics();
    writer.labelValueRow(f.text("TWR_PERCENT"), f.percent(m.twrPercent())
        + (m.twrAnnualizedPercent() == null ? "" : " / " + f.percent(m.twrAnnualizedPercent()) + " p.a."));
    writer.labelValueRow(f.text("MWR_PERCENT"),
        m.mwrPercent() == null ? f.text(m.mwrStatus().name())
            : f.percent(m.mwrPercent())
                + (m.mwrAnnualizedPercent() == null ? "" : " / " + f.percent(m.mwrAnnualizedPercent()) + " p.a."));
    writer.paragraph(f.text("TWR_PERCENT_TOOLTIP"));
    writer.paragraph(f.text("MWR_PERCENT_TOOLTIP"));
    List<PdfTable.Row> rows = new ArrayList<>();
    for (Figure figure : FIGURES) {
      rows.add(new PdfTable.Row(List.of(f.text(figure.key()),
          f.money(figure.value().applyAsDouble(period.getFirstDayTotals()), data.currency()),
          f.money(figure.value().applyAsDouble(period.getLastDayTotals()), data.currency()),
          f.money(figure.value().applyAsDouble(period.getDifference()), data.currency()))));
    }
    new PdfTable(writer,
        List.of(left(data.currency(), 2.1f), right(f.date(data.request().dateFrom), 1.2f),
            right(f.date(data.request().dateTo), 1.2f), right(f.text("DIFFERENCE"), 1.2f)),
        8, f.text("gt.report.continued")).draw(rows);
    writer.paragraph(f.text("gt.report.cumulative.note"));
  }

  static PdfTable.Column left(String title, float width) {
    return new PdfTable.Column(title, width, PdfTable.Alignment.LEFT, true);
  }

  static PdfTable.Column right(String title, float width) {
    return new PdfTable.Column(title, width, PdfTable.Alignment.RIGHT, false);
  }

  static PdfTable.Row row(String label, String value) {
    return new PdfTable.Row(List.of(label, value));
  }
}
