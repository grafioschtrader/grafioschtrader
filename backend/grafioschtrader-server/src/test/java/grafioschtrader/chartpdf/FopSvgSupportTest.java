package grafioschtrader.chartpdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.model.EventSeverity;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * Guards the one thing the time-series chart PDF depends on that no compiler checks: that FOP, as it sits on the
 * class path of this module, can embed an SVG exported by Plotly. Batik needs {@code org.w3c.dom.svg.SVGDocument},
 * which only {@code xml-apis-ext} provides. When that artifact is excluded from the FOP dependency again, FOP logs
 * "Batik not in class path" and the generation fails with a {@code NoClassDefFoundError}, but only for a document
 * that actually contains an SVG, so nothing else in the build would notice.
 *
 * <p>
 * The sample {@code pdf/plotly-time-series.svg} is a real {@code Plotly.toImage(..., {format: 'svg'})} result of a
 * line chart with purchase and sale markers.
 * </p>
 */
class FopSvgSupportTest {

  private static final String FO_TEMPLATE = """
      <?xml version="1.0" encoding="UTF-8"?>
      <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format" font-family="Helvetica">
        <fo:layout-master-set>
          <fo:simple-page-master master-name="a4" page-width="210mm" page-height="297mm" margin="20mm">
            <fo:region-body/>
          </fo:simple-page-master>
        </fo:layout-master-set>
        <fo:page-sequence master-reference="a4">
          <fo:flow flow-name="xsl-region-body">
            <fo:block>
              <fo:instream-foreign-object content-width="170mm">%s</fo:instream-foreign-object>
            </fo:block>
          </fo:flow>
        </fo:page-sequence>
      </fo:root>
      """;

  @Test
  @DisplayName("FOP embeds a Plotly SVG as vector content with extractable text")
  void plotlySvgIsEmbeddedAsVector() throws Exception {
    String svg = new String(getClass().getResourceAsStream("/pdf/plotly-time-series.svg").readAllBytes(),
        StandardCharsets.UTF_8);
    List<String> errors = new ArrayList<>();

    byte[] pdf = render(String.format(FO_TEMPLATE, svg), errors);

    assertTrue(errors.isEmpty(), "FOP reported errors: " + errors);
    assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      assertEquals(1, doc.getNumberOfPages());
      String text = new PDFTextStripper().getText(doc);
      // Title, axis ticks and legend entries are drawn as text, not as a bitmap or as outlines. The rotated y-axis
      // title is left out on purpose: PDFTextStripper does not extract text turned by 90 degrees.
      assertTrue(text.contains("end of day prices"), "title missing in extracted text: " + text);
      assertTrue(text.contains("Jan 2022"), "x-axis ticks missing in extracted text: " + text);
      assertTrue(text.contains("Purchase") && text.contains("Sale"), "legend missing in extracted text: " + text);
    }
  }

  /**
   * Renders the FO document to PDF. The document is parsed to a DOM first: FOP renders a page while the source is
   * still being parsed, and an event raised in that moment (a font substitution, for instance) loads FOP's event
   * model with a second parse of the same parser, which fails with "FWK005 parse may not be called while parsing".
   *
   * @param fo the XSL-FO document
   * @param errors receives the formatted message of every event of severity ERROR or FATAL
   * @return the generated PDF
   */
  private byte[] render(String fo, List<String> errors) throws Exception {
    FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());
    FOUserAgent userAgent = fopFactory.newFOUserAgent();
    userAgent.getEventBroadcaster().addEventListener(event -> {
      if (event.getSeverity() == EventSeverity.ERROR || event.getSeverity() == EventSeverity.FATAL) {
        errors.add(EventFormatter.format(event));
      }
    });
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    Document dom = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(fo.getBytes(StandardCharsets.UTF_8)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, userAgent, out);
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.transform(new DOMSource(dom), new SAXResult(fop.getDefaultHandler()));
    return out.toByteArray();
  }
}
