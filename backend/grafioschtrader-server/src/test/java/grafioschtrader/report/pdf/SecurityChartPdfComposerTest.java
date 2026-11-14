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
 * carries the number of its marker. The numbers must come from the rows and not from their position, because the
 * chart may show only some of the transactions.
 */
class SecurityChartPdfComposerTest {

  private static final SvgPdfConverter CONVERTER = new SvgPdfConverter();
  private static final LocalDate CREATED = LocalDate.of(2026, 11, 14);

  private static ReportFormatter formatter(Locale language) {
    return new ReportFormatter(language, Locale.forLanguageTag("de-CH"), PerformanceReportSampleWriterTest.messages(),
        currency -> 2);
  }

  private static PDDocument chart() throws Exception {
    String svg = new String(SecurityChartPdfComposerTest.class.getResourceAsStream("/pdf/plotly-time-series.svg")
        .readAllBytes(), StandardCharsets.UTF_8);
    return CONVERTER.convert(ChartSvgSanitizer.sanitize(svg));
  }

  private static Row row(int number, LocalDate date, String type, boolean splitAdjusted) {
    return new Row(number, date, type, 50.0, 101.2, -5060.0, "CHF", splitAdjusted);
  }

  private static List<String> lines(String text) {
    return text.lines().map(String::strip).toList();
  }

  @Test
  @DisplayName("Rows keep the number of their marker, the chart text stays real text and a split note is printed")
  void rowsCarryTheNumbersOfTheChart() throws Exception {
    List<Row> rows = List.of(row(2, LocalDate.of(2022, 3, 14), "ACCUMULATE", false),
        row(5, LocalDate.of(2023, 6, 1), "ACCUMULATE", true), row(7, LocalDate.of(2024, 7, 2), "REDUCE", false));

    byte[] pdf = new SecurityChartPdfComposer().compose(
        new Content("ACME Corp.", "USD", "Portfolio Main", chart(), rows, CREATED), formatter(Locale.ENGLISH),
        PDRectangle.A4);

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertEquals(1, document.getNumberOfPages());
      assertEquals(PDRectangle.A4.getWidth(), document.getPage(0).getMediaBox().getWidth(), 0.01);
      List<String> lines = lines(new PDFTextStripper().getText(document));
      String text = String.join("\n", lines);
      assertTrue(text.contains("Chart: ACME Corp.") && text.contains("Portfolio Main · Prices in USD"), text);
      assertTrue(text.contains("Jan 2022"), "the chart text must be part of the page: " + text);
      assertTrue(text.contains("Transactions in the chart"), text);
      assertTrue(lines.stream().anyMatch(l -> l.startsWith("2 ") && l.contains("14.03.2022") && l.contains("Buy")),
          text);
      assertTrue(lines.stream().anyMatch(l -> l.startsWith("5 ") && l.contains("01.06.2023")), text);
      assertTrue(lines.stream().anyMatch(l -> l.startsWith("7 ") && l.contains("02.07.2024") && l.contains("Sell")),
          text);
      assertTrue(text.contains("Markers in the chart sit at split-adjusted prices"), text);
      assertTrue(text.contains("Page 1 / 1"), text);
    }
  }

  @Test
  @DisplayName("Letter paper and German texts change the page size and every label")
  void letterPaperAndGerman() throws Exception {
    byte[] pdf = new SecurityChartPdfComposer().compose(
        new Content("ACME Corp.", "USD", "Portfolio Main", chart(),
            List.of(row(1, LocalDate.of(2022, 3, 14), "ACCUMULATE", false)), CREATED),
        formatter(Locale.GERMAN), PDRectangle.LETTER);

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertEquals(PDRectangle.LETTER.getWidth(), document.getPage(0).getMediaBox().getWidth(), 0.01);
      assertEquals(PDRectangle.LETTER.getHeight(), document.getPage(0).getMediaBox().getHeight(), 0.01);
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Diagramm: ACME Corp.") && text.contains("Kurse in USD"), text);
      assertTrue(text.contains("Transaktionen im Diagramm") && text.contains("Nr."), text);
      assertTrue(text.contains("Kaufen"), text);
      assertFalse(text.contains("split-adjusted") || text.contains("splitbereinigten"),
          "no note without a split-adjusted marker: " + text);
    }
  }

  @Test
  @DisplayName("A chart without transactions is a page with the chart only")
  void chartOnly() throws Exception {
    byte[] pdf = new SecurityChartPdfComposer().compose(
        new Content("ACME Corp.", "USD", "All portfolios", chart(), List.of(), CREATED), formatter(Locale.ENGLISH),
        PDRectangle.A4);

    try (PDDocument document = Loader.loadPDF(pdf)) {
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
      rows.add(row(i, LocalDate.of(2020, 1, 1).plusDays(i * 7L), i % 2 == 0 ? "REDUCE" : "ACCUMULATE", false));
    }

    byte[] pdf = new SecurityChartPdfComposer().compose(
        new Content("ACME Corp.", "USD", "All portfolios", chart(), rows, CREATED), formatter(Locale.ENGLISH),
        PDRectangle.A4);

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertTrue(document.getNumberOfPages() >= 3, "150 rows need several pages");
      String all = new PDFTextStripper().getText(document);
      List<String> lines = lines(all);
      for (int i = 1; i <= 150; i++) {
        String number = i + " ";
        assertEquals(1, lines.stream().filter(l -> l.startsWith(number) && l.contains(".20")).count(),
            "row " + i + " must appear exactly once");
      }
      long headings = lines.stream().filter(l -> l.startsWith("No.") || l.equals("No.")).count();
      assertTrue(headings >= document.getNumberOfPages() - 1, "the heading repeats on continuation pages: " + headings);
    }
  }
}
