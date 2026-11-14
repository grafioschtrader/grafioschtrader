package grafioschtrader.report.pdf;

import static grafioschtrader.report.pdf.PdfTable.Alignment.LEFT;
import static grafioschtrader.report.pdf.PdfTable.Alignment.RIGHT;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

/**
 * Sets the document of a time-series chart: the chart as vector graphic, followed by the table of the transactions
 * whose markers carry a number in the chart. It knows nothing about where the data comes from, so it is also the unit
 * that tests lay out without a database.
 */
@Component
public class SecurityChartPdfComposer {

  /**
   * A transaction of the table.
   *
   * @param number    the number printed at its marker in the chart
   * @param date      booking date
   * @param typeKey   name of the transaction type, translated through the message bundle
   * @param units     booked units, null for a booking without units
   * @param price     booked price per unit in the security currency, null for a booking without price
   * @param amount    amount on the cash account in the currency of that account, null when not booked
   * @param currency  currency of the cash account
   * @param splitAdjusted whether the marker sits at a split-adjusted price that differs from the booked price
   */
  public record Row(int number, LocalDate date, String typeKey, Double units, Double price, Double amount,
      String currency, boolean splitAdjusted) {
  }

  /**
   * What the document shows.
   *
   * @param securityName   name of the security
   * @param securityCurrency currency of the prices
   * @param scope          words for where the transactions come from, such as the portfolio name
   * @param chart          the converted chart; this composer closes it
   * @param rows           the numbered transactions in the order of their booking, empty for a chart without table
   * @param created        the day of creation printed in the footer
   */
  public record Content(String securityName, String securityCurrency, String scope, PDDocument chart, List<Row> rows,
      LocalDate created) {
  }

  /**
   * Builds the PDF.
   *
   * @param content   the chart and the rows
   * @param formatter language, number format and translation of the document
   * @param paper     portrait paper size
   * @return the complete document
   */
  public byte[] compose(Content content, ReportFormatter formatter, PDRectangle paper) throws IOException {
    String title = formatter.text("gt.report.chart.title", content.securityName());
    String scope = content.scope() + " · " + formatter.text("gt.report.chart.priced.in", content.securityCurrency());
    try (PdfDocumentWriter writer = new PdfDocumentWriter(title, scope, null, formatter.date(content.created()),
        formatter, paper)) {
      writer.startContent();
      writer.drawPdfPage(content.chart(), writer.width());
      if (!content.rows().isEmpty()) {
        writer.heading(formatter.text("gt.report.chart.transactions"));
        new PdfTable(writer, columns(formatter), 8, formatter.text("gt.report.continued"))
            .draw(content.rows().stream().map(row -> toTableRow(row, formatter)).toList());
        if (content.rows().stream().anyMatch(Row::splitAdjusted)) {
          writer.paragraph(formatter.text("gt.report.chart.split.note"));
        }
      }
      return writer.toByteArray();
    } finally {
      content.chart().close();
    }
  }

  /** Figures wrap instead of failing: a table cell wider than its column aborts the whole document in PdfTable. */
  private List<PdfTable.Column> columns(ReportFormatter f) {
    return List.of(new PdfTable.Column(f.text("gt.report.chart.number"), 0.7f, RIGHT, false),
        new PdfTable.Column(f.text("gt.report.booking.date"), 1.4f, LEFT, true),
        new PdfTable.Column(f.text("transaction.type"), 1.5f, LEFT, true),
        new PdfTable.Column(f.text("gt.report.units"), 1.3f, RIGHT, true),
        new PdfTable.Column(f.text("gt.report.booking.price"), 1.3f, RIGHT, true),
        new PdfTable.Column(f.text("gt.report.account.amount"), 2f, RIGHT, true));
  }

  private PdfTable.Row toTableRow(Row row, ReportFormatter f) {
    String amount = row.amount() == null ? "–" : f.money(row.amount(), row.currency()) + " " + row.currency();
    return new PdfTable.Row(List.of(Integer.toString(row.number()), f.date(row.date()), f.text(row.typeKey()),
        row.units() == null ? "" : f.units(row.units()), row.price() == null ? "" : f.price(row.price()), amount));
  }
}
