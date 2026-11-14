package grafioschtrader.report.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.model.EventSeverity;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import grafioschtrader.report.pdf.ChartSvgSanitizer.ChartSvg;

/**
 * Turns a checked chart SVG into a one-page vector PDF of exactly the size of the drawing, which
 * {@link PdfDocumentWriter#drawPdfPage} then places into a report. FOP and Batik do the conversion because PDFBox
 * cannot read SVG. The text of the chart is set in the bundled Noto Sans, the font of every other report, and stays
 * real text in the PDF.
 *
 * <p>
 * The fonts come from the class path through a {@code classpath:} scheme of FOP's resource resolver, so no file has to
 * exist on disk and the converter works the same from a test and from the executable jar. FOP's font cache is off:
 * it would write a file into the home directory of the user the server runs as, to save reading two fonts.
 * </p>
 */
@Component
public class SvgPdfConverter {

  private static final Logger log = LoggerFactory.getLogger(SvgPdfConverter.class);
  private static final String FO_NAMESPACE = "http://www.w3.org/1999/XSL/Format";
  private static final String FOP_CONFIGURATION = """
      <fop version="1.0">
        <use-cache>false</use-cache>
        <renderers>
          <renderer mime="application/pdf">
            <fonts>
              <font embed-url="classpath:/fonts/NotoSans-Regular.ttf" embedding-mode="subset" kerning="yes">
                <font-triplet name="%1$s" style="normal" weight="normal"/>
              </font>
              <font embed-url="classpath:/fonts/NotoSans-Bold.ttf" embedding-mode="subset" kerning="yes">
                <font-triplet name="%1$s" style="normal" weight="bold"/>
              </font>
            </fonts>
          </renderer>
        </renderers>
      </fop>
      """;
  /** A page without margins that holds the drawing and nothing else. */
  private static final String PAGE = """
      <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
        <fo:layout-master-set>
          <fo:simple-page-master master-name="chart" page-width="%1$spt" page-height="%2$spt" margin="0">
            <fo:region-body/>
          </fo:simple-page-master>
        </fo:layout-master-set>
        <fo:page-sequence master-reference="chart">
          <fo:flow flow-name="xsl-region-body">
            <fo:block line-height="0pt" font-size="0pt">
              <fo:instream-foreign-object content-width="%1$spt" content-height="%2$spt"/>
            </fo:block>
          </fo:flow>
        </fo:page-sequence>
      </fo:root>
      """;

  private volatile FopFactory fopFactory;

  /**
   * Converts a chart to a PDF page.
   *
   * @param chart the chart accepted by {@link ChartSvgSanitizer#sanitize}
   * @return the PDF with one page as large as the drawing in points; the caller owns and must close it
   * @throws Exception when FOP reports an error while drawing the SVG
   */
  public PDDocument convert(ChartSvg chart) throws Exception {
    List<String> errors = new ArrayList<>();
    FopFactory factory = factory();
    FOUserAgent userAgent = factory.newFOUserAgent();
    userAgent.getEventBroadcaster().addEventListener(event -> {
      if (event.getSeverity() == EventSeverity.ERROR || event.getSeverity() == EventSeverity.FATAL) {
        errors.add(EventFormatter.format(event));
      } else if (event.getSeverity() == EventSeverity.WARN) {
        log.warn("FOP: {}", EventFormatter.format(event));
      }
    });
    // The page is parsed completely before FOP starts: an event raised while a page is rendered inside a running
    // parse (a font substitution, for instance) would otherwise fail with "parse may not be called while parsing".
    Document page = page(chart);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Fop fop = factory.newFop(MimeConstants.MIME_PDF, userAgent, out);
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.transform(new DOMSource(page), new SAXResult(fop.getDefaultHandler()));
    if (!errors.isEmpty()) {
      throw new IOException("The chart could not be converted to PDF: " + errors);
    }
    return Loader.loadPDF(out.toByteArray());
  }

  private Document page(ChartSvg chart) throws Exception {
    String fo = String.format(Locale.ROOT, PAGE, chart.width(), chart.height());
    DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
    builder.setNamespaceAware(true);
    Document document = builder.newDocumentBuilder()
        .parse(new ByteArrayInputStream(fo.getBytes(StandardCharsets.UTF_8)));
    Node holder = document.getElementsByTagNameNS(FO_NAMESPACE, "instream-foreign-object").item(0);
    holder.appendChild(document.importNode(chart.root(), true));
    return document;
  }

  private FopFactory factory() throws Exception {
    FopFactory factory = fopFactory;
    if (factory == null) {
      synchronized (this) {
        if (fopFactory == null) {
          fopFactory = createFactory();
        }
        factory = fopFactory;
      }
    }
    return factory;
  }

  private FopFactory createFactory() throws Exception {
    var resolvers = ResourceResolverFactory
        .createSchemeAwareResourceResolverBuilder(ResourceResolverFactory.createDefaultResourceResolver());
    resolvers.registerResourceResolverForScheme("classpath", new ClasspathResolver());
    String configuration = String.format(FOP_CONFIGURATION, ChartSvgSanitizer.FONT_FAMILY);
    // The base must be a URL that Java can open: FOP turns it into one for every SVG. Nothing is resolved against it,
    // because the fonts have absolute URIs and the checked SVG contains no reference.
    FopConfParser parser = new FopConfParser(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)),
        URI.create("file:///"), resolvers.build());
    return parser.getFopFactoryBuilder().build();
  }

  /** Serves {@code classpath:/path} from the class loader of this module. */
  private static final class ClasspathResolver implements ResourceResolver {
    @Override
    public Resource getResource(URI uri) throws IOException {
      InputStream stream = SvgPdfConverter.class.getResourceAsStream(uri.getPath());
      if (stream == null) {
        throw new IOException("Resource not found on the class path: " + uri);
      }
      return new Resource(stream);
    }

    @Override
    public OutputStream getOutputStream(URI uri) throws IOException {
      throw new IOException("The class path is read-only: " + uri);
    }
  }
}
