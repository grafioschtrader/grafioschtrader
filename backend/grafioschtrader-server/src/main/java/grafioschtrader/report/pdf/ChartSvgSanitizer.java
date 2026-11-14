package grafioschtrader.report.pdf;

import java.io.StringReader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import grafiosch.exceptions.DataViolationException;

/**
 * Admits only the kind of SVG that Plotly draws for a chart and refuses everything else. The SVG arrives from the
 * browser and Batik, which renders it for the PDF, would otherwise follow references to files and web addresses, and
 * run scripts. Nothing is repaired: a document with an element or attribute outside the list is rejected as a whole,
 * because a chart that silently lost parts would be printed as if it were complete.
 *
 * <p>
 * The checks are: a parser without document type declarations and external entities, a fixed list of SVG elements,
 * no event handler attributes, no {@code href} at all, references in {@code url(...)} only to an id inside the
 * document, and size limits. The one change made to an accepted document is the font: every {@code font-family} is
 * replaced by the bundled font, because the PDF can only embed that one.
 * </p>
 */
public final class ChartSvgSanitizer {

  /** The bundled font family, also registered under this name in {@link SvgPdfConverter}. */
  public static final String FONT_FAMILY = "Noto Sans";

  /** Largest accepted document in characters; a ten-year candlestick chart with indicators is around 2 million. */
  static final int MAX_CHARACTERS = 8_000_000;
  static final int MAX_ELEMENTS = 200_000;
  static final float MIN_SIDE = 100;
  static final float MAX_SIDE = 4000;

  private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  private static final Set<String> ELEMENTS = Set.of("svg", "g", "defs", "clipPath", "path", "rect", "line", "circle",
      "ellipse", "polygon", "polyline", "text", "tspan");
  private static final Pattern URL_REFERENCE = Pattern.compile("url\\(\\s*(['\"]?)([^)]*)\\)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern FONT_FAMILY_DECLARATION = Pattern.compile("font-family\\s*:[^;]*",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern FORBIDDEN_STYLE = Pattern.compile("@import|expression\\s*\\(|javascript:|behavior\\s*:",
      Pattern.CASE_INSENSITIVE);

  /**
   * A checked chart.
   *
   * @param root   the {@code svg} element, owned by its own document
   * @param width  the drawing width in SVG pixels
   * @param height the drawing height in SVG pixels
   */
  public record ChartSvg(Element root, float width, float height) {
  }

  private ChartSvgSanitizer() {
  }

  /**
   * Parses and checks a chart.
   *
   * @param svg the SVG text of {@code Plotly.toImage(..., {format: 'svg'})}
   * @return the checked chart with the bundled font applied
   * @throws DataViolationException when the text is not well-formed, uses anything outside the admitted set, or has an
   *                                unusable size
   */
  public static ChartSvg sanitize(String svg) {
    if (svg == null || svg.isBlank() || svg.length() > MAX_CHARACTERS) {
      throw invalid();
    }
    Document document = parse(svg);
    Element root = document.getDocumentElement();
    if (!SVG_NAMESPACE.equals(root.getNamespaceURI()) || !"svg".equals(root.getLocalName())) {
      throw invalid();
    }
    int[] count = new int[1];
    check(root, count);
    float width = pixels(root.getAttribute("width"));
    float height = pixels(root.getAttribute("height"));
    if (width < MIN_SIDE || height < MIN_SIDE || width > MAX_SIDE || height > MAX_SIDE) {
      throw invalid();
    }
    return new ChartSvg(root, width, height);
  }

  private static Document parse(String svg) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
    } catch (Exception _) {
      throw invalid();
    }
  }

  private static void check(Element element, int[] count) {
    if (++count[0] > MAX_ELEMENTS || !SVG_NAMESPACE.equals(element.getNamespaceURI())
        || !ELEMENTS.contains(element.getLocalName())) {
      throw invalid();
    }
    NamedNodeMap attributes = element.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      checkAttribute((Attr) attributes.item(i));
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element childElement) {
        check(childElement, count);
      } else if (child.getNodeType() != Node.TEXT_NODE && child.getNodeType() != Node.COMMENT_NODE
          && child.getNodeType() != Node.CDATA_SECTION_NODE) {
        throw invalid();
      }
    }
  }

  private static void checkAttribute(Attr attribute) {
    String name = attribute.getName();
    String lower = name.toLowerCase();
    String value = attribute.getValue();
    if (name.equals("xmlns") || name.startsWith("xmlns:")) {
      return;
    }
    boolean link = lower.equals("href") || lower.endsWith(":href");
    if (lower.startsWith("on") || link || FORBIDDEN_STYLE.matcher(value).find()) {
      throw invalid();
    }
    checkUrlReferences(value);
    if (lower.equals("font-family")) {
      attribute.setValue(FONT_FAMILY);
    } else if (lower.equals("style")) {
      String declaration = Matcher.quoteReplacement("font-family: " + FONT_FAMILY);
      attribute.setValue(FONT_FAMILY_DECLARATION.matcher(value).replaceAll(declaration));
    }
  }

  /** A reference is only allowed to an id of the same document, as clip paths do. */
  private static void checkUrlReferences(String value) {
    Matcher matcher = URL_REFERENCE.matcher(value);
    while (matcher.find()) {
      if (!matcher.group(2).strip().replaceAll("^['\"]", "").startsWith("#")) {
        throw invalid();
      }
    }
  }

  /** Reads a length in SVG pixels; a unit other than {@code px} is not produced by Plotly and is refused. */
  private static float pixels(String value) {
    try {
      return Float.parseFloat(value.endsWith("px") ? value.substring(0, value.length() - 2) : value);
    } catch (NumberFormatException _) {
      throw invalid();
    }
  }

  private static DataViolationException invalid() {
    return new DataViolationException("report.chart.svg", "gt.report.chart.svg.invalid", null);
  }
}
