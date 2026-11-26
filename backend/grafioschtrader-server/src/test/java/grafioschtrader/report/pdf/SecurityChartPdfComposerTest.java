package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.report.pdf.SecurityChartPdfComposer.Content;
import grafioschtrader.report.pdf.SecurityChartPdfComposer.Row;

/**
 * Lays out the chart report without a database: the chart as vector graphic first, then the table in which every row
 * carries the number of its marker, with the gain of the row, and at its end the hypothetical sale of the open
 * position. The numbers must come from the rows and not from their position, because the chart may show only some of
 * the transactions.
 */
class SecurityChartPdfComposerTest {

  private static final SvgPdfConverter CONVERTER = new SvgPdfConverter();
  private static final LocalDate CREATED = LocalDate.of(2026, 11, 14);
  /** The thousands separator of the Swiss number format. */
  private static final String APOSTROPHE = "’";

  private static ReportFormatter formatter(Locale language) {
    return new ReportFormatter(language, Locale.forLanguageTag("de-CH"), PerformanceReportSampleWriterTest.messages(),
        _ -> 2);
  }

  private static PDDocument chart() throws Exception {
    String svg = new String(SecurityChartPdfComposerTest.class.getResourceAsStream("/pdf/plotly-time-series.svg")
        .readAllBytes(), StandardCharsets.UTF_8);
    return CONVERTER.convert(ChartSvgSanitizer.sanitize(svg));
  }

  /** A booked transaction of 50 units at 101.20, paid from a CHF account. */
  private static Row booked(int number, LocalDate date, String type, Double gain, Double gainPercentage,
      boolean splitAdjusted, boolean gainIncomplete) {
    return new Row(number, date, type, 50.0, 101.2, -5060.0, "CHF", gain, gainPercentage, splitAdjusted,
        gainIncomplete, false, false);
  }

  private static Row booked(int number, LocalDate date, String type) {
    return booked(number, date, type, null, null, false, false);
  }

  /** The open position of 40 units valued at 130.00, calculated on the day of the report. */
  private static Row hypothetical(Double gain, Double gainPercentage, boolean estimateIncomplete) {
    return new Row(null, CREATED, "HYPOTHETICAL_SELL", 40.0, 130.0, 5200.0, "USD", gain, gainPercentage, false, false,
        true, estimateIncomplete);
  }

  private static byte[] compose(List<Row> rows, Locale language, PDRectangle paper) throws Exception {
    return compose("CH0012345678", "ACME", rows, language, paper);
  }

  private static byte[] compose(String isin, String tickerSymbol, List<Row> rows, Locale language, PDRectangle paper)
      throws Exception {
    return new SecurityChartPdfComposer().compose(
        new Content("ACME Corp.", "USD", isin, tickerSymbol, "Portfolio Main", chart(), rows, CREATED),
        formatter(language), paper);
  }

  private static List<String> lines(String text) {
    return text.lines().map(String::strip).toList();
  }

  @Test
  @DisplayName("Rows keep the number of their marker, show the gain and the chart text stays real text")
  void rowsCarryTheNumbersOfTheChart() throws Exception {
    List<Row> rows = List.of(booked(2, LocalDate.of(2022, 3, 14), "ACCUMULATE"),
        booked(5, LocalDate.of(2023, 6, 1), "ACCUMULATE", null, null, true, false),
        booked(7, LocalDate.of(2024, 7, 2), "REDUCE", 250.0, 12.5, false, false));

    try (PDDocument document = Loader.loadPDF(compose(rows, Locale.ENGLISH, PDRectangle.A4))) {
      assertEquals(1, document.getNumberOfPages());
      assertEquals(PDRectangle.A4.getWidth(), document.getPage(0).getMediaBox().getWidth(), 0.01);
      List<String> lines = lines(new PDFTextStripper().getText(document));
      String text = String.join("\n", lines);
      assertTrue(text.contains("Chart: ACME Corp.") && text.contains("Portfolio Main · Prices in USD"), text);
      assertTrue(lines.contains("ISIN CH0012345678 · Ticker ACME"), "the identification line: " + text);
      assertTrue(text.contains("Jan 2022"), "the chart text must be part of the page: " + text);
      assertTrue(text.contains("Transactions in the chart"), text);
      assertTrue(text.contains("Quantity") && text.contains("Gain"), "columns of the application table: " + text);
      assertTrue(lines.stream().anyMatch(l -> l.startsWith("2 ") && l.contains("14.03.2022") && l.contains("Buy")),
          text);
      assertTrue(lines.stream().anyMatch(l -> l.startsWith("5 ") && l.contains("01.06.2023")), text);
      assertTrue(lines.stream().anyMatch(
          l -> l.startsWith("7 ") && l.contains("02.07.2024") && l.contains("Sell") && l.contains("250.00")
              && l.endsWith("12.50")),
          "gain and gain percentage of the sale: " + text);
      assertFalse(lines.stream().anyMatch(l -> l.startsWith("2 ") && l.contains("250.00")), "a purchase has no gain");
      assertTrue(text.contains("Markers in the chart sit at split-adjusted prices"), text);
      assertFalse(text.contains("hypothetical"), "no open position, no hypothetical sale: " + text);
      assertTrue(text.contains("Page 1 / 1"), text);
    }
  }

  @Test
  @DisplayName("The hypothetical sale closes the table without number, and each incomplete value is flagged")
  void hypotheticalSaleAndIncompleteValues() throws Exception {
    List<Row> rows = List.of(booked(1, LocalDate.of(2022, 3, 14), "ACCUMULATE"),
        booked(2, LocalDate.of(2024, 7, 2), "REDUCE", 1234.5, 8.25, false, true),
        hypothetical(600.0, 12.0, true));

    try (PDDocument document = Loader.loadPDF(compose(rows, Locale.ENGLISH, PDRectangle.A4))) {
      List<String> lines = lines(new PDFTextStripper().getText(document));
      String text = String.join("\n", lines);
      int sale = indexOf(lines, l -> l.startsWith("2 ") && l.contains("1" + APOSTROPHE + "234.50 *")
          && l.endsWith("8.25"));
      // The type column wraps, so the words of the type may stand on a line of their own
      int hypothetical = indexOf(lines, l -> l.startsWith("14.11.2026") && l.contains("Sell")
          && l.contains("600.00") && l.endsWith("12.00"));
      assertTrue(sale >= 0, "the incomplete gain carries a star: " + text);
      assertTrue(hypothetical > sale, "the hypothetical sale is the last row and has no number: " + text);
      assertTrue(text.contains("The hypothetical sale values the units still held at the latest price"), text);
      assertTrue(text.contains("* The purchases of this security account do not cover the sale completely"), text);
      assertTrue(text.contains("The estimated costs of the hypothetical sale are incomplete"), text);
    }
  }

  @Test
  @DisplayName("An open position without any marker in the view still shows its hypothetical sale")
  void hypotheticalSaleWithoutMarkers() throws Exception {
    try (PDDocument document = Loader.loadPDF(
        compose(List.of(hypothetical(600.0, 12.0, false)), Locale.ENGLISH, PDRectangle.A4))) {
      List<String> lines = lines(new PDFTextStripper().getText(document));
      String text = String.join("\n", lines);
      assertTrue(text.contains("Transactions in the chart"), text);
      assertTrue(lines.stream().anyMatch(l -> l.startsWith("14.11.2026") && l.contains("Sell")), text);
      assertFalse(text.contains("incomplete"), "no flag, no note about incomplete values: " + text);
    }
  }

  @Test
  @DisplayName("Letter paper and German texts change the page size and every label")
  void letterPaperAndGerman() throws Exception {
    List<Row> rows = List.of(booked(1, LocalDate.of(2022, 3, 14), "ACCUMULATE"), hypothetical(600.0, 12.0, false));

    try (PDDocument document = Loader.loadPDF(compose(rows, Locale.GERMAN, PDRectangle.LETTER))) {
      assertEquals(PDRectangle.LETTER.getWidth(), document.getPage(0).getMediaBox().getWidth(), 0.01);
      assertEquals(PDRectangle.LETTER.getHeight(), document.getPage(0).getMediaBox().getHeight(), 0.01);
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Diagramm: ACME Corp.") && text.contains("Kurse in USD"), text);
      assertTrue(text.contains("Transaktionen im Diagramm") && text.contains("Nr."), text);
      assertTrue(text.contains("Anzahl") && text.contains("Gewinn"), "columns of the application table: " + text);
      // The type column wraps, and on the narrower Letter page "Verkauf hypothetisch" takes two lines.
      assertTrue(text.contains("Kaufen") && text.contains("Verkauf") && text.contains("hypothetisch"), text);
      assertTrue(text.contains("Der hypothetische Verkauf bewertet"), text);
      assertFalse(text.contains("splitbereinigten"), "no note without a split-adjusted marker: " + text);
    }
  }

  @Test
  @DisplayName("ISIN and ticker stand above the chart, each only when the security has it")
  void identifiersAreOptional() throws Exception {
    List<Row> rows = List.of(booked(1, LocalDate.of(2022, 3, 14), "ACCUMULATE"));

    try (PDDocument document = Loader.loadPDF(compose(null, "ACME", rows, Locale.GERMAN, PDRectangle.A4))) {
      List<String> lines = lines(new PDFTextStripper().getText(document));
      assertTrue(lines.contains("Ticker ACME"), "only the ticker: " + lines);
      assertFalse(lines.stream().anyMatch(l -> l.contains("ISIN")), "no ISIN, no label: " + lines);
    }
    try (PDDocument document = Loader.loadPDF(compose(" CH0012345678 ", "  ", rows, Locale.GERMAN, PDRectangle.A4))) {
      List<String> lines = lines(new PDFTextStripper().getText(document));
      assertTrue(lines.contains("ISIN CH0012345678"), "only the ISIN, trimmed: " + lines);
      assertFalse(lines.stream().anyMatch(l -> l.contains("Ticker")), "a blank ticker shows nothing: " + lines);
    }
    try (PDDocument document = Loader.loadPDF(compose(null, null, rows, Locale.GERMAN, PDRectangle.A4))) {
      String text = new PDFTextStripper().getText(document);
      assertFalse(text.contains("ISIN") || text.contains("Ticker"), "neither is shown: " + text);
    }
  }

  @Test
  @DisplayName("No German heading is broken inside a word on A4, the narrower paper")
  void germanHeadingsStayWholeOnA4() throws Exception {
    List<Row> rows = List.of(booked(1, LocalDate.of(2022, 3, 14), "ACCUMULATE"));

    try (PDDocument document = Loader.loadPDF(compose(rows, Locale.GERMAN, PDRectangle.A4))) {
      List<String> lines = lines(new PDFTextStripper().getText(document));
      for (String heading : List.of("Buchungsdatum", "Transaktionstyp", "Anzahl", "Gewinn")) {
        assertTrue(lines.stream().anyMatch(l -> l.contains(heading)), heading + " is broken or missing: " + lines);
      }
    }
  }

  @Test
  @DisplayName("A chart without transactions is a page with the chart only")
  void chartOnly() throws Exception {
    try (PDDocument document = Loader.loadPDF(compose(List.of(), Locale.ENGLISH, PDRectangle.A4))) {
      assertEquals(1, document.getNumberOfPages());
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Jan 2022"), text);
      assertFalse(text.contains("Transactions in the chart"), text);
    }
  }

  @Test
  @DisplayName("A long table continues on further pages, repeats its heading and loses no row")
  void longTableContinues() throws Exception {
    List<Row> rows = new ArrayList<>();
    for (int i = 1; i <= 150; i++) {
      rows.add(booked(i, LocalDate.of(2020, 1, 1).plusDays(i * 7L), i % 2 == 0 ? "REDUCE" : "ACCUMULATE"));
    }
    rows.add(hypothetical(600.0, 12.0, false));

    try (PDDocument document = Loader.loadPDF(compose(rows, Locale.ENGLISH, PDRectangle.A4))) {
      assertTrue(document.getNumberOfPages() >= 3, "150 rows need several pages");
      List<String> lines = lines(new PDFTextStripper().getText(document));
      for (int i = 1; i <= 150; i++) {
        String number = i + " ";
        assertEquals(1, lines.stream().filter(l -> l.startsWith(number) && l.contains(".20")).count(),
            "row " + i + " must appear exactly once");
      }
      // Only the hypothetical sale has a gain of 600.00; the footer of each page starts with the same date
      assertEquals(1, lines.stream().filter(l -> l.startsWith("14.11.2026") && l.contains("600.00")).count(),
          "one hypothetical sale");
      long headings = lines.stream().filter(l -> l.startsWith("No.") || l.equals("No.")).count();
      assertTrue(headings >= document.getNumberOfPages() - 1, "the heading repeats on continuation pages: " + headings);
    }
  }

  private static int indexOf(List<String> lines, java.util.function.Predicate<String> matches) {
    for (int i = 0; i < lines.size(); i++) {
      if (matches.test(lines.get(i))) {
        return i;
      }
    }
    return -1;
  }
}
