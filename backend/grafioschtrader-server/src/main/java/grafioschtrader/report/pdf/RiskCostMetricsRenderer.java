package grafioschtrader.report.pdf;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.types.PerformanceReportSection;

/** Four metric groups matching the screen, with explanations for figures that cannot be calculated. */
@Component
public class RiskCostMetricsRenderer implements SectionRenderer {
  private record Metric(String key, Object value, String format) {
  }

  public PerformanceReportSection section() {
    return PerformanceReportSection.RISK_COST_METRICS;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.PORTRAIT);
    var m = data.period().getMetrics();
    if (m == null) {
      writer.paragraph(f.text("gt.report.no.holdings"));
      return;
    }
    List<Metric> returns = List.of(p("TWR_PERCENT", m.twrPercent()),
        p("TWR_ANNUALIZED_PERCENT", m.twrAnnualizedPercent()), p("MWR_PERCENT", m.mwrPercent()),
        p("MWR_ANNUALIZED_PERCENT", m.mwrAnnualizedPercent()), p("MAX_DRAWDOWN_PERCENT", m.maxDrawdownPercent()));
    List<Metric> risk = List.of(d("DRAWDOWN_PEAK_DATE", m.drawdownPeakDate()),
        d("DRAWDOWN_TROUGH_DATE", m.drawdownTroughDate()), d("DRAWDOWN_RECOVERY_DATE", m.drawdownRecoveryDate()),
        p("CURRENT_DRAWDOWN_PERCENT", m.currentDrawdownPercent()),
        p("VOLATILITY_ANNUALIZED_PERCENT", m.volatilityAnnualizedPercent()),
        p("BEST_STEP_PERCENT", m.bestStepPercent()), d("BEST_STEP_DATE", m.bestStepDate()),
        p("WORST_STEP_PERCENT", m.worstStepPercent()), d("WORST_STEP_DATE", m.worstStepDate()));
    List<Metric> costs = List.of(new Metric("AVERAGE_CAPITAL", m.averageCapitalMC(), "money"),
        new Metric("gt.report.booked.fees", m.feesWithoutRate() > 0 ? "n/a" : f.money(m.feesMC(), data.currency()),
            "text"),
        p("FEE_RATIO_PERCENT", m.feeRatioPercent()),
        p("FEE_RATIO_ANNUALIZED_PERCENT", m.feeRatioAnnualizedPercent()));
    List<Metric> basis = List.of(n("CALENDAR_DAYS", m.calendarDays()), n("EXPECTED_SESSIONS", m.expectedSessions()),
        n("VALUED_SESSIONS", m.valuedSessions()), n("GAP_INTERVALS", m.gapIntervals()),
        n("RETURN_OBSERVATIONS", m.returnObservations()), n("FEES_WITHOUT_RATE", m.feesWithoutRate()),
        new Metric("MWR_STATUS", f.text(m.mwrStatus().name()), "text"));
    grid(writer, f, data, "PERFORMANCE_RETURN", returns, "PERFORMANCE_RISK", risk);
    grid(writer, f, data, "PERFORMANCE_COST", costs, "PERFORMANCE_DATA_BASIS", basis);
    if (m.feesWithoutRate() > 0) {
      writer.paragraph(f.text("gt.report.known.fees", f.money(m.feesMC(), data.currency()), data.currency()));
    }
    writer.paragraph(f.text("gt.report.null.metrics"));
    writer.paragraph(f.text("VOLATILITY_ANNUALIZED_PERCENT_TOOLTIP"));
    writer.paragraph(f.text("BEST_STEP_PERCENT_TOOLTIP"));
    writer.paragraph(f.text("FEES_TOOLTIP"));
    writer.paragraph(f.text("DRAWDOWN_RECOVERY_DATE_TOOLTIP"));
  }

  private void grid(PdfDocumentWriter w, ReportFormatter f, ReportData data, String leftTitle, List<Metric> left,
      String rightTitle, List<Metric> right) throws IOException {
    List<PdfTable.Row> rows = new ArrayList<>();
    for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
      Metric l = i < left.size() ? left.get(i) : null;
      Metric r = i < right.size() ? right.get(i) : null;
      rows.add(new PdfTable.Row(List.of(l == null ? "" : f.text(l.key()), l == null ? "" : value(l, data, f),
          r == null ? "" : f.text(r.key()), r == null ? "" : value(r, data, f))));
    }
    new PdfTable(w,
        List.of(PerformanceSummaryRenderer.left(f.text(leftTitle), 1.8f), PerformanceSummaryRenderer.left("", 1.1f),
            PerformanceSummaryRenderer.left(f.text(rightTitle), 1.8f), PerformanceSummaryRenderer.left("", 1.1f)),
        8, f.text("gt.report.continued")).draw(rows);
  }

  private String value(Metric m, ReportData data, ReportFormatter f) {
    if (m.value() == null) {
      if (m.key().equals("MWR_PERCENT")) {
        return f.text(data.period().getMetrics().mwrStatus().name());
      }
      return "–";
    }
    return switch (m.format()) {
    case "percent" -> f.percent((Double) m.value());
    case "date" -> f.date((LocalDate) m.value());
    case "money" -> f.money((Double) m.value(), data.currency());
    case "count" -> f.number(((Number) m.value()).doubleValue(), 0, 0);
    default -> m.value().toString();
    };
  }

  private static Metric p(String key, Double value) {
    return new Metric(key, value, "percent");
  }

  private static Metric d(String key, LocalDate value) {
    return new Metric(key, value, "date");
  }

  private static Metric n(String key, int value) {
    return new Metric(key, value, "count");
  }
}
