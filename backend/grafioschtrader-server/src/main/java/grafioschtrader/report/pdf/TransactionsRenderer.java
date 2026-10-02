package grafioschtrader.report.pdf;

import static grafioschtrader.report.pdf.HoldingsData.money;
import static grafioschtrader.report.pdf.PdfTable.Alignment.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.types.PerformanceReportSection;

/** Complete booking list, with transfer labels and automatic continuation pages from PdfTable. */
@Component
public class TransactionsRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.TRANSACTIONS;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.LANDSCAPE, 200);
    writer.paragraph(f.text("gt.report.transactions.method"));
    writer.paragraph(f.text("gt.report.booking.conversion"));
    writer.paragraph(f.text("gt.report.transfer.method"));
    writer.paragraph(f.text("gt.report.valued.in", data.currency()));
    var columns = List.of(column(f, "gt.report.booking.date", 1.25f, LEFT), column(f, "transaction.type", 1.2f, LEFT),
        column(f, "gt.report.instrument.account", 2.4f, LEFT), column(f, "gt.report.units", 1.3f, RIGHT),
        column(f, "gt.report.booking.price", 1.35f, RIGHT), column(f, "gt.report.account.amount", 1.85f, RIGHT),
        column(f, "gt.report.exchange.rate", 1.25f, RIGHT), column(f, "gt.report.value.mc", 1.65f, RIGHT));
    List<PdfTable.Row> rows = new ArrayList<>();
    for (var b : data.bookings().transactions()) {
      var t = b.transaction();
      String type = t.isCashaccountTransfer() ? f.text("gt.report.cash.transfer")
          : t.getIdSecurityTransfer() != null ? f.text("gt.report.security.transfer")
              : f.text(t.getTransactionType().name());
      String instrument = t.getSecurity() == null ? t.getCashaccount().getName()
          : t.getSecurity().getName() + (t.getSecurity().getIsin() == null ? "" : "\n" + t.getSecurity().getIsin());
      rows.add(new PdfTable.Row(List.of(f.date(t.getTransactionDateAsLocalDate()), type, instrument,
          t.getUnits() == null ? "" : f.units(t.getUnits()), t.getQuotation() == null ? "" : f.price(t.getQuotation()),
          f.money(t.getCashaccountAmount(), t.getCashaccount().getCurrency()) + " " + t.getCashaccount().getCurrency(),
          Double.isFinite(b.rate()) ? f.rate(b.rate()) : "n/a", money(b.amount(), data, f))));
    }
    new PdfTable(writer, columns, 7, f.text("gt.report.continued")).draw(rows);
  }

  private PdfTable.Column column(ReportFormatter f, String key, float width, PdfTable.Alignment alignment) {
    return new PdfTable.Column(f.text(key), width, alignment, alignment == LEFT);
  }
}
