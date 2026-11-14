package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.report.pdf.ChartSvgSanitizer.ChartSvg;

/**
 * The SVG of a chart report comes from the browser and is rendered on the server. These tests pin what keeps that safe:
 * a real Plotly export passes, and each way of reaching a file, a web address or a script is refused.
 */
class ChartSvgSanitizerTest {

  private static final String HEAD = "<svg xmlns=\"http://www.w3.org/2000/svg\" "
      + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"700\" height=\"350\">";

  @Test
  @DisplayName("A Plotly export passes, keeps its size and is set in the bundled font")
  void plotlyExportPasses() throws IOException {
    String svg = new String(getClass().getResourceAsStream("/pdf/plotly-time-series.svg").readAllBytes(),
        StandardCharsets.UTF_8);
    assertTrue(svg.contains("Arial"), "the sample must carry a font the PDF does not have");

    ChartSvg chart = ChartSvgSanitizer.sanitize(svg);

    assertEquals(700f, chart.width());
    assertEquals(350f, chart.height());
    NodeList texts = chart.root().getElementsByTagNameNS("http://www.w3.org/2000/svg", "text");
    assertTrue(texts.getLength() > 0);
    for (int i = 0; i < texts.getLength(); i++) {
      String style = ((Element) texts.item(i)).getAttribute("style");
      assertTrue(style.contains("font-family: " + ChartSvgSanitizer.FONT_FAMILY), style);
      assertFalse(style.contains("Arial"), style);
    }
  }

  @Test
  @DisplayName("Every way to reach a file, a web address or a script is refused")
  void dangerousDocumentsAreRefused() {
    Map<String, String> refused = new LinkedHashMap<>();
    refused.put("image element", HEAD + "<image width=\"10\" height=\"10\" href=\"http://example.org/a.png\"/></svg>");
    refused.put("script element", HEAD + "<script>alert(1)</script></svg>");
    refused.put("foreignObject", HEAD + "<foreignObject><div/></foreignObject></svg>");
    refused.put("use element", HEAD + "<use xlink:href=\"#a\"/></svg>");
    refused.put("event handler", HEAD + "<g onload=\"alert(1)\"/></svg>");
    refused.put("href on a path", HEAD + "<path xlink:href=\"file:///etc/passwd\" d=\"M0 0\"/></svg>");
    refused.put("web url in style", HEAD + "<rect style=\"fill: url(http://example.org/p)\"/></svg>");
    refused.put("web url in attribute", HEAD + "<rect fill=\"url(file:///c:/secret)\"/></svg>");
    refused.put("css import", HEAD + "<g style=\"@import url(#a)\"/></svg>");
    refused.put("document type", "<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" + HEAD + "&x;</svg>");
    refused.put("other namespace", "<svg xmlns=\"http://example.org/\" width=\"700\" height=\"350\"/>");
    refused.put("not xml", "<svg");
    refused.put("too small", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>");
    refused.put("too large", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"90000\" height=\"350\"/>");
    refused.put("no size", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
    refused.put("empty", " ");

    for (Map.Entry<String, String> entry : refused.entrySet()) {
      assertThrows(DataViolationException.class, () -> ChartSvgSanitizer.sanitize(entry.getValue()), entry.getKey());
    }
  }

  @Test
  @DisplayName("A reference to an id inside the document stays possible, as clip paths need it")
  void referenceToOwnIdIsAllowed() {
    String svg = HEAD + "<defs><clipPath id=\"c\"><rect width=\"5\" height=\"5\"/></clipPath></defs>"
        + "<g clip-path=\"url(#c)\" style=\"font-family: 'Open Sans', verdana; fill: red\"/></svg>";

    ChartSvg chart = ChartSvgSanitizer.sanitize(svg);

    Element group = (Element) chart.root().getElementsByTagNameNS("http://www.w3.org/2000/svg", "g").item(0);
    assertEquals("url(#c)", group.getAttribute("clip-path"));
    assertEquals("font-family: Noto Sans; fill: red", group.getAttribute("style"));
  }
}
