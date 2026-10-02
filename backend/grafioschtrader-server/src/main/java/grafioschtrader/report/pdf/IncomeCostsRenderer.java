package grafioschtrader.report.pdf;

import static grafioschtrader.report.pdf.HoldingsData.money;
import static grafioschtrader.report.pdf.PdfTable.Alignment.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.PerformanceReportSection;

/** Recorded period costs and absolute position contributions, with an explicit reconciliation to performance. */
@Component
public class IncomeCostsRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.INCOME_COSTS;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.LANDSCAPE, 240);
    writer.paragraph(f.text("gt.report.recorded.costs.method"));
    writer.paragraph(f.text("gt.report.booking.conversion"));
    writer.paragraph(f.text("gt.report.contribution.method"));
    writer.paragraph(f.text("gt.report.transfer.method"));
    writer.paragraph(f.text("gt.report.valued.in", data.currency()));
    var columns = List.of(column(f, "gt.report.instrument", 2.8f, LEFT),
        column(f, "gt.report.period.income", 1.5f, RIGHT), column(f, "gt.report.withholding.tax", 1.5f, RIGHT),
        column(f, "gt.report.transaction.costs", 1.5f, RIGHT), column(f, "gt.report.transaction.taxes", 1.5f, RIGHT),
        column(f, "gt.report.financing", 1.5f, RIGHT), column(f, "gt.report.contribution", 1.6f, RIGHT));
    List<PdfTable.Row> rows = new ArrayList<>();
    for (AssetclassType type : AssetclassType.values()) {
      var group = data.bookings().positions().stream()
          .filter(p -> p.security().getAssetClass().getCategoryType() == type).toList();
      if (group.isEmpty()) {
        continue;
      }
      rows.add(new PdfTable.Row(List.of(f.text(type.name()), "", "", "", "", "", ""), PdfTable.Kind.GROUP));
      for (var p : group) {
        rows.add(row(
            p.security().getName() + (p.transfer() ? " [T]" : "")
                + (p.security().getIsin() == null ? "" : "\n" + p.security().getIsin()),
            p, data, f, PdfTable.Kind.NORMAL));
      }
      rows.add(row(f.text("gt.report.subtotal"), total(group), data, f, PdfTable.Kind.SUBTOTAL));
    }
    rows.add(
        row(f.text("gt.report.securities.total"), total(data.bookings().positions()), data, f, PdfTable.Kind.TOTAL));
    new PdfTable(writer, columns, 7, f.text("gt.report.continued")).draw(rows);
    accountTotals(data, writer, f);
  }

  private void accountTotals(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    var b = data.bookings();
    writer.ensureSpace(180);
    writer.heading(f.text("gt.report.cash"));
    writer.labelValueRow(f.text("FEE"), money(-b.fees(), data, f));
    writer.labelValueRow(f.text("INTEREST_CASHACCOUNT"), money(b.interest(), data, f));
    if (b.accountTransactionCosts() != 0) {
      writer.labelValueRow(f.text("gt.report.account.transaction.costs"), money(b.accountTransactionCosts(), data, f));
    }
    writer.labelValueRow(f.text("gt.report.recorded.costs"), money(b.recordedCosts(), data, f));
    var metrics = data.period().getMetrics();
    Double capital = metrics == null ? null : metrics.averageCapitalMC();
    writer.labelValueRow(f.text("gt.report.recorded.cost.ratio"),
        capital != null && capital > 0 && Double.isFinite(b.recordedCosts())
            ? f.percent(b.recordedCosts() / capital * 100)
            : "n/a");
    writer.paragraph(f.text("gt.report.recorded.cost.ratio.method"));
    double remainder = data.period().getDifference().getTotalGainMC() - b.attributedGain();
    if (!Double.isFinite(remainder)
        || Math.abs(remainder) > Math.pow(10, -f.currencyPrecision().apply(data.currency()))) {
      writer.labelValueRow(f.text("gt.report.unattributed"), money(remainder, data, f));
      writer.paragraph(f.text("gt.report.unattributed.method"));
    }
  }

  private BookingData.Position total(List<BookingData.Position> positions) {
    return new BookingData.Position(null, false, positions.stream().mapToDouble(BookingData.Position::income).sum(),
        positions.stream().mapToDouble(BookingData.Position::withholdingTax).sum(),
        positions.stream().mapToDouble(BookingData.Position::transactionCosts).sum(),
        positions.stream().mapToDouble(BookingData.Position::transactionTaxes).sum(),
        positions.stream().mapToDouble(BookingData.Position::financing).sum(),
        positions.stream().mapToDouble(BookingData.Position::contribution).sum());
  }

  private PdfTable.Row row(String name, BookingData.Position p, ReportData data, ReportFormatter f,
      PdfTable.Kind kind) {
    return new PdfTable.Row(List.of(name, money(p.income(), data, f), money(p.withholdingTax(), data, f),
        money(p.transactionCosts(), data, f), money(p.transactionTaxes(), data, f), money(p.financing(), data, f),
        money(p.contribution(), data, f)), kind);
  }

  private PdfTable.Column column(ReportFormatter f, String key, float width, PdfTable.Alignment alignment) {
    return new PdfTable.Column(f.text(key), width, alignment, alignment == LEFT);
  }
}
