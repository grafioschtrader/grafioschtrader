package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportLayoutTest {
  @Test
  @DisplayName("Long booking lists preserve every identifier and repeat headings without clipped glyphs or amounts")
  void bookingBounds() throws Exception {
    for (String language : List.of("de", "en")) {
      try (var document = Loader.loadPDF(PerformanceReportSampleWriterTest.bookingSample(language, 90))) {
        var stripper = new PDFTextStripper() {
          @Override
          protected void writeString(String text, List<TextPosition> positions) throws IOException {
            for (TextPosition p : positions) {
              assertTrue(p.getXDirAdj() >= 35, text);
              assertTrue(p.getXDirAdj() + p.getWidthDirAdj() <= getCurrentPage().getMediaBox().getWidth() - 35, text);
              if (text.contains("CH1234567890") || text.contains("WWWW")) {
                assertTrue(p.getYDirAdj() <= getCurrentPage().getMediaBox().getHeight() - PdfDocumentWriter.BOTTOM,
                    text);
              }
            }
            super.writeString(text, positions);
          }
        };
        String text = stripper.getText(document);
        assertEquals(93, text.split("CH1234567890", -1).length - 1); // 92 security bookings plus position row
        int transactionPages = 0;
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
          stripper.setStartPage(page);
          stripper.setEndPage(page);
          String pageText = stripper.getText(document);
          if (pageText.contains("CH1234567890") && !pageText.contains("[T]")) {
            transactionPages++;
            assertTrue(pageText.contains(language.equals("de") ? "Buchungsdatum" : "Booking date"));
          }
        }
        assertTrue(transactionPages >= 3);
      }
    }
  }

  @Test
  @DisplayName("Statement tables preserve identifiers and keep every glyph inside the printable page")
  void statementBounds() throws Exception {
    var positions = new ArrayList<grafioschtrader.reportviews.securityaccount.SecurityPositionSummary>();
    for (int i = 0; i < 35; i++) {
      positions.add(HoldingsReportTest.position(i + 1, "Long investment name " + "W".repeat(70),
          grafioschtrader.types.AssetclassType.EQUITIES, "EUR", 12345678.9));
    }
    var request = HoldingsReportTest.request("de");
    request.detailColumns = true;
    try (var document = Loader.loadPDF(HoldingsReportTest.generate(HoldingsReportTest.summary(positions), request))) {
      var stripper = new PDFTextStripper() {
        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
          float width = getCurrentPage().getMediaBox().getWidth();
          for (TextPosition p : positions) {
            assertTrue(p.getXDirAdj() >= 35, text);
            assertTrue(p.getXDirAdj() + p.getWidthDirAdj() <= width - 35, text);
          }
          super.writeString(text, positions);
        }
      };
      assertEquals(35, stripper.getText(document).split("CH1234567890", -1).length - 1);
    }
  }

  static ReportFormatter formatter() {
    return new ReportFormatter(Locale.ENGLISH, Locale.forLanguageTag("de-CH"),
        PerformanceReportSampleWriterTest.messages(), c -> c.equals("JPY") ? 0 : 2);
  }

  @Test
  @DisplayName("Display precision is currency aware and preserves tiny units and five significant rate digits")
  void precision() {
    var f = formatter();
    assertEquals("1’235", f.money(1234.6, "JPY"));
    assertEquals("1’234.60", f.money(1234.6, "CHF"));
    assertEquals("0.00000001", f.units(.00000001));
    assertEquals("12.30", f.price(12.3));
    assertEquals("0.00012346", f.rate(.000123456));
    assertEquals("0.12346", f.rate(.123456));
    assertEquals("0.00 %", f.percent(0.0));
    assertEquals("–", f.percent(null));
  }

  @Test
  @DisplayName("Nice ticks cover signed, constant, tiny and missing series in four to six steps")
  void ticks() {
    for (var values : List.of(List.of(-11.0, 35.0), List.of(100.0, 100.0), List.of(0.0), List.of(.00001, .000024),
        Arrays.asList(null, 2.0))) {
      var scale = PdfChartDrawer.scale(values, false);
      assertTrue(scale.ticks().size() >= 4 && scale.ticks().size() <= 6, scale.toString());
      values.stream().filter(java.util.Objects::nonNull).forEach(v -> {
        assertTrue(scale.min() <= v);
        assertTrue(scale.max() >= v);
      });
    }
  }

  @Test
  @DisplayName("Six maximum-width address lines remain inside the envelope window without losing text")
  void addressWindow() throws Exception {
    String line = "W".repeat(45);
    try (var writer = new PdfDocumentWriter("Test", "Scope", "", "2025", formatter())) {
      writer.newPage(PdfDocumentWriter.Orientation.PORTRAIT, true);
      writer.address(String.join("\n", java.util.Collections.nCopies(6, line)), 56.7f, 704, 241, 10);
      try (var document = Loader.loadPDF(writer.toByteArray())) {
        var stripper = new PDFTextStripper() {
          @Override
          protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (text.contains("WWW")) {
              for (TextPosition position : positions) {
                assertTrue(position.getXDirAdj() >= 56.6f);
                assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= 297.8f);
                assertTrue(position.getYDirAdj() >= 137 && position.getYDirAdj() <= 209);
              }
            }
            super.writeString(text, positions);
          }
        };
        assertEquals(6, stripper.getText(document).lines().filter(line::equals).count());
      }
    }
  }

  @Test
  @DisplayName("Table headings repeat and ordinary rows stay together; page-tall text continues without loss")
  void tablePagination() throws Exception {
    try (var writer = new PdfDocumentWriter("Test", "Scope", "Sender", "2025", formatter())) {
      writer.startContent();
      List<PdfTable.Row> rows = new ArrayList<>();
      for (int i = 0; i < 80; i++) {
        rows.add(new PdfTable.Row(List.of("Row " + i + "\nSecond line " + i, "123.45")));
      }
      rows.add(new PdfTable.Row(List.of("Long text ".repeat(1500) + "END OF LONG ROW", "42.00")));
      new PdfTable(writer, List.of(PerformanceSummaryRenderer.left("Repeated heading", 3),
          PerformanceSummaryRenderer.right("Amount", 1)), 9, "Continued").draw(rows);
      try (var document = Loader.loadPDF(writer.toByteArray())) {
        var stripper = new PDFTextStripper();
        String all = stripper.getText(document);
        assertTrue(all.contains("END OF LONG ROW"));
        assertTrue(all.contains("Continued"));
        for (int i = 1; i <= document.getNumberOfPages(); i++) {
          stripper.setStartPage(i);
          stripper.setEndPage(i);
          String page = stripper.getText(document);
          assertTrue(page.contains("Repeated heading"));
          for (int row = 0; row < 80; row++) {
            if (page.contains("Row " + row + "\n")) {
              assertTrue(page.contains("Second line " + row + "\n"));
            }
          }
        }
      }
    }
  }
}
