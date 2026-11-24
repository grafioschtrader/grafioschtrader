package grafioschtrader.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import grafiosch.exceptions.DataViolationException;

/**
 * Validates and sanitizes user-uploaded SVG logos before persistence. Only a strict allowlist of static SVG elements
 * and safe attributes is accepted; active content, external references, embedded styles, filters and oversized payloads
 * are rejected as a whole. Already-deployed static catalog SVGs are never passed through this sanitizer.
 */
public final class LogoSvgSanitizer {

  /** Maximum accepted SVG payload in UTF-8 bytes, enforced both before and after re-serialization. */
  public static final int MAX_BYTES = 32_768;

  /** Maximum number of SVG elements admitted in a single uploaded logo. */
  public static final int MAX_ELEMENTS = 2_000;

  private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

  private static final Set<String> ALLOWED_ELEMENTS = Set.of("svg", "g", "defs", "title", "desc", "symbol", "use",
      "clipPath", "mask", "linearGradient", "radialGradient", "stop", "path", "rect", "circle", "ellipse", "line",
      "polygon", "polyline", "text", "tspan");

  private static final Set<String> LOCAL_HREF_ELEMENTS = Set.of("use", "linearGradient", "radialGradient");

  private static final Pattern URL_REFERENCE = Pattern.compile("url\\(\\s*(['\"]?)([^)]*)\\)",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern FORBIDDEN_VALUE = Pattern
      .compile("javascript\\s*:|data\\s*:|@import|expression\\s*\\(|behavior\\s*:", Pattern.CASE_INSENSITIVE);

  private static final Pattern VIEW_BOX_SEPARATOR = Pattern.compile("[\\s,]+");

  /**
   * Re-serialized sanitized SVG and its lowercase SHA-256 hex digest.
   *
   * @param svgContent    re-serialized UTF-8 SVG markup without comments or processing instructions
   * @param contentSha256 lowercase 64-character hex SHA-256 of {@code svgContent}
   */
  public record SanitizedSvg(String svgContent, String contentSha256) {
  }

  private LogoSvgSanitizer() {
  }

  /**
   * Validates, normalizes (adding a {@code viewBox} from numeric {@code width}/{@code height} when absent, stripping
   * comments) and re-serializes a user-uploaded SVG logo.
   *
   * @param rawSvg raw SVG markup submitted by a client
   * @return sanitized markup and its SHA-256 digest
   * @throws DataViolationException with {@code gt.logo.file.too.large} when the byte limit is exceeded, or
   *                                {@code gt.logo.svg.invalid} when parsing or security validation fails
   */
  public static SanitizedSvg sanitize(String rawSvg) {
    if (rawSvg == null || rawSvg.isBlank()) {
      throw invalid();
    }
    if (rawSvg.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw tooLarge();
    }
    Document document = parse(rawSvg);
    Element root = document.getDocumentElement();
    if (root == null || !SVG_NAMESPACE.equals(root.getNamespaceURI()) || !"svg".equals(root.getLocalName())) {
      throw invalid();
    }
    ensureValidViewBox(root);
    int[] elementCount = new int[1];
    checkElement(root, elementCount);
    String serialized = serialize(document);
    byte[] utf8 = serialized.getBytes(StandardCharsets.UTF_8);
    if (utf8.length > MAX_BYTES) {
      throw tooLarge();
    }
    return new SanitizedSvg(serialized, sha256Hex(utf8));
  }

  /** Computes the lowercase 64-character SHA-256 hex digest of UTF-8 text. */
  public static String sha256Hex(String utf8Text) {
    return sha256Hex(utf8Text.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
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
      Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
      stripTopLevelCommentsAndRejectPI(document);
      return document;
    } catch (DataViolationException e) {
      throw e;
    } catch (Exception _) {
      throw invalid();
    }
  }

  private static void stripTopLevelCommentsAndRejectPI(Document document) {
    List<Node> toRemove = new ArrayList<>();
    for (Node child = document.getFirstChild(); child != null; child = child.getNextSibling()) {
      short type = child.getNodeType();
      if (type == Node.COMMENT_NODE) {
        toRemove.add(child);
      } else if (type != Node.ELEMENT_NODE) {
        throw invalid();
      }
    }
    toRemove.forEach(document::removeChild);
  }

  private static void ensureValidViewBox(Element root) {
    String viewBox = root.getAttribute("viewBox");
    if (viewBox != null && !viewBox.isBlank()) {
      String[] parts = VIEW_BOX_SEPARATOR.split(viewBox.trim());
      if (parts.length != 4) {
        throw invalid();
      }
      double minX = parseFiniteDouble(parts[0]);
      double minY = parseFiniteDouble(parts[1]);
      double width = parseFiniteDouble(parts[2]);
      double height = parseFiniteDouble(parts[3]);
      if (!Double.isFinite(minX) || !Double.isFinite(minY) || width <= 0 || height <= 0) {
        throw invalid();
      }
      return;
    }
    double width = parsePositiveDimension(root.getAttribute("width"));
    double height = parsePositiveDimension(root.getAttribute("height"));
    root.setAttribute("viewBox", "0 0 " + formatNumber(width) + " " + formatNumber(height));
  }

  private static double parseFiniteDouble(String token) {
    try {
      double value = Double.parseDouble(token);
      if (!Double.isFinite(value)) {
        throw invalid();
      }
      return value;
    } catch (NumberFormatException _) {
      throw invalid();
    }
  }

  private static double parsePositiveDimension(String raw) {
    if (raw == null || raw.isBlank()) {
      throw invalid();
    }
    String trimmed = raw.trim();
    if (trimmed.toLowerCase(Locale.ROOT).endsWith("px")) {
      trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
    }
    double value = parseFiniteDouble(trimmed);
    if (value <= 0) {
      throw invalid();
    }
    return value;
  }

  private static String formatNumber(double value) {
    if (value == Math.rint(value) && Math.abs(value) < 1e15) {
      return Long.toString((long) value);
    }
    return Double.toString(value);
  }

  private static void checkElement(Element element, int[] count) {
    String localName = element.getLocalName();
    if (++count[0] > MAX_ELEMENTS || !SVG_NAMESPACE.equals(element.getNamespaceURI())
        || !ALLOWED_ELEMENTS.contains(localName)) {
      throw invalid();
    }
    NamedNodeMap attributes = element.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      checkAttribute(localName, (Attr) attributes.item(i));
    }
    List<Node> commentsToRemove = new ArrayList<>();
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element childElement) {
        checkElement(childElement, count);
      } else if (child.getNodeType() == Node.COMMENT_NODE) {
        commentsToRemove.add(child);
      } else if (child.getNodeType() != Node.TEXT_NODE && child.getNodeType() != Node.CDATA_SECTION_NODE) {
        throw invalid();
      }
    }
    commentsToRemove.forEach(element::removeChild);
  }

  private static void checkAttribute(String elementLocalName, Attr attribute) {
    String name = attribute.getName();
    String lower = name.toLowerCase(Locale.ROOT);
    String value = attribute.getValue();
    if (name.equals("xmlns") || name.startsWith("xmlns:")) {
      return;
    }
    if (lower.startsWith("on") || FORBIDDEN_VALUE.matcher(value).find()) {
      throw invalid();
    }
    boolean isHref = lower.equals("href") || lower.endsWith(":href");
    if (isHref) {
      String trimmed = value.trim();
      if (!LOCAL_HREF_ELEMENTS.contains(elementLocalName) || !trimmed.startsWith("#") || trimmed.length() < 2) {
        throw invalid();
      }
    }
    checkUrlReferences(value);
  }

  private static void checkUrlReferences(String value) {
    Matcher matcher = URL_REFERENCE.matcher(value);
    while (matcher.find()) {
      String target = matcher.group(2).strip().replaceAll("^['\"]|['\"]$", "").strip();
      if (!target.startsWith("#") || target.length() < 2) {
        throw invalid();
      }
    }
  }

  private static String serialize(Document document) {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      StringWriter writer = new StringWriter();
      transformer.transform(new DOMSource(document), new StreamResult(writer));
      return writer.toString();
    } catch (Exception _) {
      throw invalid();
    }
  }

  private static DataViolationException invalid() {
    return new DataViolationException("svg.content", "gt.logo.svg.invalid", null);
  }

  private static DataViolationException tooLarge() {
    return new DataViolationException("svg.content", "gt.logo.file.too.large", null);
  }
}
