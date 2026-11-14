package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.report.pdf.ChartSvgSanitizer.ChartSvg;

/**
 * Checks what the chart report needs from the conversion: one page of exactly the drawing size, the text of the chart
 * as text in the bundled Noto Sans, and vector content instead of a bitmap. Runs without a database.
 */
class SvgPdfConverterTest {

  @Test
  @DisplayName("A Plotly SVG becomes one vector page of the drawing size with embedded Noto Sans text")
  void plotlySvgBecomesVectorPage() throws Exception {
    String svg = new String(getClass().getResourceAsStream("/pdf/plotly-time-series.svg").readAllBytes(),
        StandardCharsets.UTF_8);
    ChartSvg chart = ChartSvgSanitizer.sanitize(svg);

    try (PDDocument pdf = new SvgPdfConverter().convert(chart)) {
      assertEquals(1, pdf.getNumberOfPages(), "the drawing must not overflow onto a second page");
      PDPage page = pdf.getPage(0);
      assertEquals(chart.width(), page.getMediaBox().getWidth(), 0.01);
      assertEquals(chart.height(), page.getMediaBox().getHeight(), 0.01);

      PDResources resources = page.getResources();
      boolean bitmap = false;
      for (COSName name : resources.getXObjectNames()) {
        bitmap |= resources.isImageXObject(name);
      }
      assertFalse(bitmap, "a chart drawn as vector contains no bitmap");
      boolean notoSans = false;
      for (COSName name : resources.getFontNames()) {
        PDFont font = resources.getFont(name);
        notoSans |= font.getName().contains("NotoSans") && font.isEmbedded();
      }
      assertTrue(notoSans, "the chart text must use the embedded bundled font");

      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("end of day prices") && text.contains("Jan 2022"), text);
    }
  }

  /**
   * The fixture is what the chart export produces for a chart like the one of the application: zoomed, with holdings
   * on a second axis, four kinds of drawn shapes and three numbered transaction markers. It passes the strict check of
   * the SVG and the numbers at the markers must survive the conversion. FOP draws a group with an opacity below 1 as a
   * bitmap and loses its text, so the export makes the marker traces opaque; this test notices when that is undone.
   */
  @Test
  @DisplayName("A chart of the application with shapes and holdings keeps the numbers at its markers")
  void applicationChartKeepsMarkerNumbers() throws Exception {
    String svg = new String(getClass().getResourceAsStream("/pdf/plotly-gt-chart.svg").readAllBytes(),
        StandardCharsets.UTF_8);

    try (PDDocument pdf = new SvgPdfConverter().convert(ChartSvgSanitizer.sanitize(svg))) {
      assertEquals(1, pdf.getNumberOfPages());
      String text = new PDFTextStripper().getText(pdf);
      List<String> lines = text.lines().map(String::strip).toList();
      // Each number is a text of its own at its marker; ticks and legend entries are extracted as joined lines.
      assertTrue(lines.containsAll(List.of("1", "2", "3")), "marker numbers missing in the chart text: " + lines);
      assertTrue(text.contains("Jan 2023") && text.contains("Buy") && text.contains("Sell"), text);
    }
  }
}
