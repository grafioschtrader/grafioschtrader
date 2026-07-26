package grafioschtrader.receipt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import grafioschtrader.entities.Transaction;
import grafioschtrader.receipt.GtReceiptDefs.Labels;
import grafioschtrader.types.TransactionType;

/**
 * Generates a one page transaction receipt PDF ("Transaktionsbeleg") for a single security transaction, branded
 * Grafioschtrader and modeled on the receipts of Swiss trading platforms.
 *
 * <p>
 * <b>Round-trip contract:</b> the PDF import extracts text with PDFBox's {@code PDFTextStripper}
 * ({@code setSortByPosition(true)}) and matches it against the Grafioschtrader import templates. The drawing code
 * therefore follows strict rules so the extracted text is deterministic:
 * </p>
 * <ul>
 * <li>Each visual row is drawn on exactly one baseline; the stripper merges everything on one baseline into one text
 * line ordered left to right.</li>
 * <li>No rotated text and no text inside images; decorative shading is drawn as vector rectangles which are invisible
 * to the stripper.</li>
 * <li>The line sequence and the label words come from {@link GtReceiptDefs} and mirror the import template files in
 * <code>src/test/resources/testdata/import_template</code> exactly.</li>
 * </ul>
 *
 * <p>
 * Bond note: GT stores percent quoted bonds already unit-normalized (cash amount = units × quotation), so the receipt
 * prints the stored units/quotation directly and does not use the import's percent flag; re-importing reproduces the
 * stored values exactly.
 * </p>
 */
public class TransactionReceiptPdfGenerator {

  /** Contextual display data that is not reachable from the transaction entity itself. */
  public record ReceiptContext(String nickname, String securityAccountName, String cashAccountName) {
  }

  private static final float PAGE_MARGIN = 50;
  private static final float RIGHT_EDGE = PDRectangle.A4.getWidth() - PAGE_MARGIN;
  private static final float VALUE_COLUMN_X = 170;
  private static final float ROW_LEADING = 16.5f;
  private static final float FONT_SIZE = 10;

  private final PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private final PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
  private final DecimalFormat amountFormat = GtReceiptDefs.createAmountFormat();
  private final DecimalFormat unitsFormat = GtReceiptDefs.createUnitsFormat();

  /**
   * Generates the receipt PDF for the given transaction.
   *
   * @param transaction a security transaction of type ACCUMULATE, REDUCE, DIVIDEND or FINANCE_COST
   * @param locale      the user's locale; German produces the German receipt, everything else the English one
   * @param context     nickname and account names for the customer block
   * @return the PDF document as byte array
   * @throws IOException if PDF creation fails
   */
  public byte[] generate(Transaction transaction, Locale locale, ReceiptContext context) throws IOException {
    Labels labels = GtReceiptDefs.forLocale(locale);
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
        Writer w = new Writer(cs, PDRectangle.A4.getHeight() - PAGE_MARGIN - 10);
        drawHeader(w, labels, context);
        switch (transaction.getTransactionType()) {
          case ACCUMULATE, REDUCE -> drawBuySell(w, transaction, labels);
          case DIVIDEND -> drawDividend(w, transaction, labels);
          case FINANCE_COST -> drawFinanceCost(w, transaction, labels);
          default -> throw new IllegalArgumentException(
              "No receipt support for transaction type " + transaction.getTransactionType());
        }
        drawFooter(w, labels);
      }
      document.save(bos);
      return bos.toByteArray();
    }
  }

  private void drawHeader(Writer w, Labels labels, ReceiptContext context) throws IOException {
    w.left(labels.receiptTitle, fontBold, 13);
    w.right("Grafioschtrader", fontBold, 13);
    w.newRow(14);
    w.hairline();
    w.newRow(20);
    labeledRow(w, labels.customer, context.nickname());
    labeledRow(w, labels.securityAccount, context.securityAccountName());
    labeledRow(w, labels.cashAccount, context.cashAccountName());
    w.newRow(10);
  }

  private void labeledRow(Writer w, String label, String value) throws IOException {
    if (value != null && !value.isBlank()) {
      w.left(label, fontBold, FONT_SIZE);
      w.at(VALUE_COLUMN_X, sanitize(value), fontRegular, FONT_SIZE);
      w.newRow(ROW_LEADING);
    }
  }

  private void drawTitleBar(Writer w, String title, Transaction transaction, Labels labels) throws IOException {
    w.shadeRow();
    w.left(title, fontBold, FONT_SIZE + 1);
    w.right(labels.reference + " " + GtReceiptDefs.REFERENCE_PREFIX + transaction.getIdTransaction(), fontRegular,
        FONT_SIZE);
    w.newRow(24);
    w.left(labels.date, fontBold, FONT_SIZE);
    w.at(VALUE_COLUMN_X, GtReceiptDefs.DATE_FORMAT.format(transaction.getTransactionTime()), fontRegular, FONT_SIZE);
    w.at(330, labels.time, fontBold, FONT_SIZE);
    w.at(420, GtReceiptDefs.TIME_FORMAT.format(transaction.getTransactionTime()), fontRegular, FONT_SIZE);
    w.newRow(ROW_LEADING);
  }

  private void drawSecurityRow(Writer w, Transaction transaction, Labels labels) throws IOException {
    String isinPart = labels.isin + " " + transaction.getSecurity().getIsin();
    w.left(fitText(sanitize(transaction.getSecurity().getName()), fontBold, FONT_SIZE, 320), fontBold, FONT_SIZE);
    w.right(isinPart, fontRegular, FONT_SIZE);
    w.newRow(ROW_LEADING + 4);
  }

  private void drawBuySell(Writer w, Transaction transaction, Labels labels) throws IOException {
    String typeWord = GtReceiptDefs.getTransactionTypeWord(transaction, labels);
    drawTitleBar(w, labels.buySellTitlePrefix + " " + typeWord, transaction, labels);
    w.newRow(6);
    drawSecurityRow(w, transaction, labels);
    valueRow(w, labels.instrumentCurrency, transaction.getSecurity().getCurrency(), false);
    valueRow(w, labels.units, unitsFormat.format(transaction.getUnits()), false);
    valueRow(w, labels.price, amountFormat.format(transaction.getQuotation()), false);
    if (isNotZero(transaction.getAssetInvestmentValue1())) {
      valueRow(w, labels.accruedInterest, amountFormat.format(transaction.getAssetInvestmentValue1()), false);
    }
    drawCostTaxRows(w, transaction, labels);
    drawExchangeRateRow(w, transaction, labels);
    valueRow(w, labels.settlementCurrency, transaction.getCashaccount().getCurrency(), false);
    w.newRow(2);
    String totalLabel = transaction.getTransactionType() == TransactionType.ACCUMULATE ? labels.debited
        : labels.credited;
    totalRow(w, totalLabel, transaction);
  }

  private void drawDividend(Writer w, Transaction transaction, Labels labels) throws IOException {
    String typeWord = GtReceiptDefs.getTransactionTypeWord(transaction, labels);
    drawTitleBar(w, typeWord, transaction, labels);
    if (transaction.getExDate() != null) {
      valueRow(w, labels.exDate, GtReceiptDefs.DATE_FORMAT.format(transaction.getExDate()), true);
    }
    w.newRow(6);
    drawSecurityRow(w, transaction, labels);
    valueRow(w, labels.instrumentCurrency, transaction.getSecurity().getCurrency(), false);
    valueRow(w, labels.units, unitsFormat.format(transaction.getUnits()), false);
    valueRow(w, labels.distributionPerUnit, amountFormat.format(transaction.getQuotation()), false);
    if (isNotZero(transaction.getTaxCost())) {
      valueRow(w, labels.taxes, amountFormat.format(transaction.getTaxCost()), false);
    }
    drawExchangeRateRow(w, transaction, labels);
    valueRow(w, labels.settlementCurrency, transaction.getCashaccount().getCurrency(), false);
    w.newRow(2);
    totalRow(w, labels.credit, transaction);
  }

  private void drawFinanceCost(Writer w, Transaction transaction, Labels labels) throws IOException {
    String typeWord = GtReceiptDefs.getTransactionTypeWord(transaction, labels);
    drawTitleBar(w, typeWord, transaction, labels);
    w.newRow(6);
    drawSecurityRow(w, transaction, labels);
    drawCostTaxRows(w, transaction, labels);
    valueRow(w, labels.settlementCurrency, transaction.getCashaccount().getCurrency(), false);
    w.newRow(2);
    totalRow(w, labels.charge, transaction);
  }

  private void drawCostTaxRows(Writer w, Transaction transaction, Labels labels) throws IOException {
    if (isNotZero(transaction.getTransactionCost())) {
      valueRow(w, labels.commission, amountFormat.format(transaction.getTransactionCost()), false);
    }
    if (isNotZero(transaction.getTaxCost())) {
      valueRow(w, labels.taxes, amountFormat.format(transaction.getTaxCost()), false);
    }
  }

  private void drawExchangeRateRow(Writer w, Transaction transaction, Labels labels) throws IOException {
    if (transaction.getCurrencyExRate() != null
        && !transaction.getSecurity().getCurrency().equals(transaction.getCashaccount().getCurrency())) {
      valueRow(w, labels.exchangeRate, amountFormat.format(transaction.getCurrencyExRate()), false);
    }
  }

  /**
   * Draws the shaded total line. The printed amount is positive: debit type receipts (buy, finance cost) show the
   * negated cash account amount, credit type receipts show it unchanged.
   */
  private void totalRow(Writer w, String label, Transaction transaction) throws IOException {
    TransactionType type = transaction.getTransactionType();
    double amount = transaction.getCashaccountAmount();
    if (type == TransactionType.ACCUMULATE || type == TransactionType.FINANCE_COST) {
      amount = -amount;
    }
    w.shadeRow();
    w.left(label, fontBold, FONT_SIZE);
    w.right(transaction.getCashaccount().getCurrency() + " " + amountFormat.format(amount), fontBold, FONT_SIZE);
    w.newRow(24);
  }

  private void valueRow(Writer w, String label, String value, boolean boldLabel) throws IOException {
    w.left(label, boldLabel ? fontBold : fontRegular, FONT_SIZE);
    w.right(value, fontRegular, FONT_SIZE);
    w.newRow(ROW_LEADING);
  }

  private void drawFooter(Writer w, Labels labels) throws IOException {
    w.newRow(14);
    w.left(labels.thanks, fontRegular, FONT_SIZE);
    w.moveToY(PAGE_MARGIN + 10);
    w.hairline();
    w.newRow(12);
    w.left(labels.createdWith, fontRegular, 8);
  }

  private static boolean isNotZero(Double value) {
    return value != null && value != 0;
  }

  /**
   * Replaces characters outside the WinAnsi range of the standard Helvetica font. User supplied values (nickname,
   * account and security names) may contain arbitrary Unicode which would make PDFBox throw on drawing.
   */
  static String sanitize(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        sb.append(' ');
      } else if ((c >= 32 && c <= 126) || (c >= 0xA1 && c <= 0xFF) || "€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ".indexOf(c) >= 0) {
        sb.append(c);
      } else {
        sb.append('?');
      }
    }
    return sb.toString().replaceAll(" +", " ").trim();
  }

  /** Truncates the text with an ellipsis so it does not exceed the given width in points. */
  private String fitText(String text, PDType1Font font, float size, float maxWidth) throws IOException {
    if (font.getStringWidth(text) / 1000 * size <= maxWidth) {
      return text;
    }
    String truncated = text;
    while (!truncated.isEmpty() && font.getStringWidth(truncated + "...") / 1000 * size > maxWidth) {
      truncated = truncated.substring(0, truncated.length() - 1);
    }
    return truncated.trim() + "...";
  }

  /**
   * Small drawing helper that keeps a vertical cursor. All text of one visual row is drawn on the same baseline; the
   * caller advances rows with {@link #newRow(float)}.
   */
  private class Writer {
    private final PDPageContentStream cs;
    private float y;

    Writer(PDPageContentStream cs, float startY) {
      this.cs = cs;
      this.y = startY;
    }

    void left(String text, PDType1Font font, float size) throws IOException {
      at(PAGE_MARGIN, text, font, size);
    }

    void right(String text, PDType1Font font, float size) throws IOException {
      float width = font.getStringWidth(text) / 1000 * size;
      at(RIGHT_EDGE - width, text, font, size);
    }

    void at(float x, String text, PDType1Font font, float size) throws IOException {
      cs.beginText();
      cs.setFont(font, size);
      cs.newLineAtOffset(x, y);
      cs.showText(text);
      cs.endText();
    }

    void newRow(float leading) {
      y -= leading;
    }

    void moveToY(float newY) {
      y = newY;
    }

    /** Draws a light gray background rectangle behind the current row. */
    void shadeRow() throws IOException {
      cs.setNonStrokingColor(0.93f);
      cs.addRect(PAGE_MARGIN - 5, y - 5, RIGHT_EDGE - PAGE_MARGIN + 10, 19);
      cs.fill();
      cs.setNonStrokingColor(0f);
    }

    void hairline() throws IOException {
      cs.setLineWidth(0.7f);
      cs.moveTo(PAGE_MARGIN, y);
      cs.lineTo(RIGHT_EDGE, y);
      cs.stroke();
    }
  }
}
