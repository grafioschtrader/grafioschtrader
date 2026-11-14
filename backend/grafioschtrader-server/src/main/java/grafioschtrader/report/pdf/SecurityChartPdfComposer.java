package grafioschtrader.report.pdf;

import static grafioschtrader.report.pdf.PdfTable.Alignment.LEFT;
import static grafioschtrader.report.pdf.PdfTable.Alignment.RIGHT;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

/**
 * Sets the document of a time-series chart: the chart as vector graphic, followed by the table of the transactions
 * whose markers carry a number in the chart and, set apart at its end, the hypothetical sale of the units still held.
 * It knows nothing about where the data comes from, so it is also the unit that tests lay out without a database.
 */
@Component
public class SecurityChartPdfComposer {

  /**
   * A row of the table: a transaction of the chart, or the hypothetical sale of the open position.
   *
   * @param number             the number printed at its marker in the chart, null for the hypothetical sale, which has
   *                           no marker
   * @param date               booking date; for the hypothetical sale the day of the valuation
   * @param typeKey            name of the transaction type, translated through the message bundle
   * @param units              units of the transaction, null for a booking without units
   * @param price              price per unit in the security currency, null for a booking without price
   * @param amount             amount on the cash account in the currency of that account, null when not booked
   * @param currency           currency of the cash account
   * @param gain               gain or loss of the row in the security currency, null where the row has none
   * @param gainPercentage     the gain in percent of the cost, null where the row has none
   * @param splitAdjusted      whether the marker sits at a split-adjusted price that differs from the booked price
   * @param gainIncomplete     whether the purchases did not cover the sale, so that the gain lacks the missing units
   * @param hypothetical       whether this is the hypothetical sale of the open position
   * @param estimateIncomplete whether the estimated costs of the hypothetical sale lack a fee model, a tax model or a
   *                           matching rule
   */
  public record Row(Integer number, LocalDate date, String typeKey, Double units, Double price, Double amount,
      String currency, Double gain, Double gainPercentage, boolean splitAdjusted, boolean gainIncomplete,
      boolean hypothetical, boolean estimateIncomplete) {
  }

  /**
   * What the document shows.
   *
   * @param securityName     name of the security
   * @param securityCurrency currency of the prices and of the gains
   * @param isin             ISIN of the security, null or blank when it has none
   * @param tickerSymbol     ticker symbol of the security, null or blank when it has none
   * @param scope            words for where the transactions come from, such as the portfolio name
   * @param chart            the converted chart; this composer closes it
   * @param rows             the numbered transactions in the order of their booking, then the hypothetical sale; empty
   *                         for a chart without table
   * @param created          the day of creation printed in the footer
   */
  public record Content(String securityName, String securityCurrency, String isin, String tickerSymbol, String scope,
      PDDocument chart, List<Row> rows, LocalDate created) {
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
      writer.paragraph(identification(content, formatter));
      writer.drawPdfPage(content.chart(), writer.width());
      if (!content.rows().isEmpty()) {
        writer.heading(formatter.text("gt.report.chart.transactions"));
        new PdfTable(writer, columns(formatter), 8, formatter.text("gt.report.continued"))
            .draw(content.rows().stream().map(row -> toTableRow(row, content.securityCurrency(), formatter)).toList());
        footnotes(content.rows(), writer, formatter);
      }
      return writer.toByteArray();
    } finally {
      content.chart().close();
    }
  }

  /**
   * The line that names the security beyond its name: ISIN and ticker symbol, each only when the security has it. It
   * stands above the chart and not in the page header, where a long name could push it out of the two lines.
   *
   * @return the line, or an empty text when the security has neither
   */
  private String identification(Content content, ReportFormatter f) {
    List<String> identifiers = new ArrayList<>();
    if (content.isin() != null && !content.isin().isBlank()) {
      identifiers.add(f.text("isin") + " " + content.isin().strip());
    }
    if (content.tickerSymbol() != null && !content.tickerSymbol().isBlank()) {
      identifiers.add(f.text("TICKER") + " " + content.tickerSymbol().strip());
    }
    return String.join(" · ", identifiers);
  }

  /**
   * The columns and names of the table of the transactions in the application, so that the document reads like the
   * screen. Figures wrap instead of failing: a table cell wider than its column aborts the whole document in PdfTable.
   */
  private List<PdfTable.Column> columns(ReportFormatter f) {
    // The widths keep the German headings "Buchungsdatum" and "Transaktionstyp" whole on A4, the narrower of the two
    // papers; the figure columns are as narrow as their usual values allow.
    return List.of(new PdfTable.Column(f.text("gt.report.chart.number"), 0.6f, RIGHT, false),
        new PdfTable.Column(f.text("gt.report.booking.date"), 1.5f, LEFT, true),
        new PdfTable.Column(f.text("transaction.type"), 1.7f, LEFT, true),
        new PdfTable.Column(f.text("QUANTITY"), 0.95f, RIGHT, true),
        new PdfTable.Column(f.text("gt.report.booking.price"), 1.05f, RIGHT, true),
        new PdfTable.Column(f.text("gt.report.account.amount"), 1.4f, RIGHT, true),
        new PdfTable.Column(f.text("GAIN"), 1.1f, RIGHT, true),
        new PdfTable.Column(f.text("GAIN_PERCENTAGE"), 1f, RIGHT, true));
  }

  /** The hypothetical sale is set apart by a line above it and bold type, the booked rows are plain. */
  private PdfTable.Row toTableRow(Row row, String securityCurrency, ReportFormatter f) {
    String amount = row.amount() == null ? "–" : f.money(row.amount(), row.currency()) + " " + row.currency();
    String gain = row.gain() == null ? ""
        : f.money(row.gain(), securityCurrency) + (row.gainIncomplete() ? " *" : "");
    String gainPercentage = row.gainPercentage() == null ? "" : f.number(row.gainPercentage(), 2, 2);
    return new PdfTable.Row(
        List.of(row.number() == null ? "" : Integer.toString(row.number()), f.date(row.date()), f.text(row.typeKey()),
            row.units() == null ? "" : f.units(row.units()), row.price() == null ? "" : f.price(row.price()), amount,
            gain, gainPercentage),
        row.hypothetical() ? PdfTable.Kind.SUBTOTAL : PdfTable.Kind.NORMAL);
  }

  /** Says what a reader cannot see in the figures; each note appears only when a row needs it. */
  private void footnotes(List<Row> rows, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    if (rows.stream().anyMatch(Row::hypothetical)) {
      writer.paragraph(f.text("gt.report.chart.hypothetical.note"));
    }
    if (rows.stream().anyMatch(Row::splitAdjusted)) {
      writer.paragraph(f.text("gt.report.chart.split.note"));
    }
    if (rows.stream().anyMatch(Row::gainIncomplete)) {
      writer.paragraph("* " + f.text("COST_BASIS_INCOMPLETE"));
    }
    if (rows.stream().anyMatch(Row::estimateIncomplete)) {
      writer.paragraph(f.text("gt.report.chart.estimate.incomplete"));
    }
  }
}
