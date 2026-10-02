package grafioschtrader.report.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import grafioschtrader.types.PerformanceReportSection;

/** Flowing A4 document with protected page furniture, outlines and a second pass for contents and page counts. */
public final class PdfDocumentWriter implements AutoCloseable {
  public enum Orientation {
    PORTRAIT, LANDSCAPE
  }

  public static final float MARGIN = 40;
  public static final float BOTTOM = 68;
  public static final Color INK = new Color(31, 48, 65);
  public static final Color PALE = new Color(235, 240, 244);
  private final PDDocument document = new PDDocument();
  private final PdfFonts fonts;
  private final ReportFormatter formatter;
  private final String title;
  private final String scope;
  private final String sender;
  private final String created;
  private final PDDocumentOutline outline = new PDDocumentOutline();
  private final Map<PerformanceReportSection, Anchor> anchors = new LinkedHashMap<>();
  private final List<TocRow> contents = new ArrayList<>();
  private PDPage page;
  private PDPageContentStream stream;
  private Orientation orientation = Orientation.PORTRAIT;
  private float y;
  private boolean cover;

  private record Anchor(PDPage page, float y, int number) {
  }

  private record TocRow(PerformanceReportSection section, PDPage page, float y) {
  }

  public PdfDocumentWriter(String title, String scope, String sender, String created, ReportFormatter formatter)
      throws IOException {
    this.title = title;
    this.scope = scope;
    this.sender = sender == null ? "" : sender.lines().findFirst().orElse("");
    this.created = created;
    this.formatter = formatter;
    fonts = PdfFonts.load(document);
    document.getDocumentCatalog().setDocumentOutline(outline);
    document.getDocumentInformation().setTitle(title);
    document.getDocumentInformation().setCreator("Grafioschtrader");
  }

  public float width() {
    return page.getMediaBox().getWidth() - 2 * MARGIN;
  }

  public float y() {
    return y;
  }

  public void y(float value) {
    y = value;
  }

  public float top() {
    return page.getMediaBox().getHeight() - 76;
  }

  public float available() {
    return y - BOTTOM;
  }

  public int pageCount() {
    return document.getNumberOfPages();
  }

  public PDPageContentStream stream() {
    return stream;
  }

  public void newPage(Orientation direction, boolean isCover) throws IOException {
    if (stream != null) {
      stream.close();
    }
    orientation = direction;
    cover = isCover;
    PDRectangle size = direction == Orientation.PORTRAIT ? PDRectangle.A4
        : new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    page = new PDPage(size);
    document.addPage(page);
    stream = new PDPageContentStream(document, page);
    y = top();
    if (!cover) {
      headerText(title, MARGIN, size.getHeight() - 39, width() * 0.47f);
      headerText(scope, MARGIN + width() * 0.51f, size.getHeight() - 39, width() * 0.49f);
      hairline(MARGIN, size.getHeight() - 58, width());
    }
  }

  private void headerText(String value, float x, float baseline, float width) throws IOException {
    List<String> lines = wrap(value, width, 8, false);
    if (lines.size() > 2) {
      String last = lines.get(1);
      while (measure(last + "…", 8, false) > width) {
        last = last.substring(0, last.length() - 1);
      }
      lines = List.of(lines.getFirst(), last + "…");
    }
    for (String line : lines) {
      text(line, x, baseline, 8, false);
      baseline -= 10;
    }
  }

  /** Returns true if a page break was necessary. */
  public boolean ensureSpace(float height) throws IOException {
    if (page == null || y - height < BOTTOM) {
      newPage(orientation, false);
      return true;
    }
    return false;
  }

  public void startContent() throws IOException {
    if (page == null || cover) {
      newPage(Orientation.PORTRAIT, false);
    }
  }

  public void beginSection(PerformanceReportSection section, Orientation direction) throws IOException {
    beginSection(section, direction, 76);
  }

  /** Reserve the introduction and first content block together so a section heading is never orphaned. */
  public void beginSection(PerformanceReportSection section, Orientation direction, float minimumHeight)
      throws IOException {
    if (page == null || section == PerformanceReportSection.COVER || direction != orientation || cover
        || section == PerformanceReportSection.TRANSACTIONS) {
      newPage(direction, section == PerformanceReportSection.COVER);
    } else {
      ensureSpace(minimumHeight);
    }
    anchors.put(section, new Anchor(page, y, document.getNumberOfPages()));
    PDOutlineItem item = new PDOutlineItem();
    item.setTitle(formatter.text(section.name()));
    item.setDestination(destination(page, y));
    outline.addLast(item);
    if (section != PerformanceReportSection.COVER) {
      heading(formatter.text(section.name()));
    }
  }

  public void heading(String value) throws IOException {
    ensureSpace(48);
    y -= 8;
    text(value, MARGIN, y, 14, true);
    y -= 26;
  }

  public void paragraph(String value) throws IOException {
    if (value == null || value.isBlank()) {
      return;
    }
    for (String line : wrap(value, width(), 9, false)) {
      ensureSpace(14);
      text(line, MARGIN, y, 9, false);
      y -= 14;
    }
    y -= 7;
  }

  public void labelValueRow(String label, String value) throws IOException {
    paragraph(label + ": " + value);
  }

  public void text(String value, float x, float baseline, float size, boolean bold) throws IOException {
    stream.beginText();
    stream.setFont(font(bold), size);
    stream.setNonStrokingColor(INK);
    stream.newLineAtOffset(x, baseline);
    stream.showText(printable(value, bold));
    stream.endText();
  }

  public void textRight(String value, float right, float baseline, float size, boolean bold) throws IOException {
    text(value, right - measure(value, size, bold), baseline, size, bold);
  }

  /** Keeps the user-supplied address lines inside the fixed envelope window, including unusually wide names. */
  public void address(String value, float x, float baseline, float width, float size) throws IOException {
    for (String line : (value == null ? "" : value).split("\\R", -1)) {
      float lineSize = Math.max(7, Math.min(size, size * width / Math.max(1, measure(line, size, false))));
      stream.saveGraphicsState();
      stream.beginText();
      stream.setFont(font(false), lineSize);
      stream.setNonStrokingColor(INK);
      stream.setHorizontalScaling(Math.min(100, 100 * width / Math.max(1, measure(line, lineSize, false))));
      stream.newLineAtOffset(x, baseline);
      stream.showText(printable(line, false));
      stream.endText();
      stream.restoreGraphicsState();
      baseline -= size * 1.4f;
    }
  }

  public float textWrapped(String value, float x, float baseline, float width, float size, boolean bold)
      throws IOException {
    List<String> lines = wrap(value, width, size, bold);
    for (String line : lines) {
      text(line, x, baseline, size, bold);
      baseline -= size * 1.4f;
    }
    return lines.size() * size * 1.4f;
  }

  public float measure(String value, float size, boolean bold) throws IOException {
    return font(bold).getStringWidth(printable(value, bold)) * size / 1000;
  }

  private PDFont font(boolean bold) {
    return bold ? fonts.bold() : fonts.regular();
  }

  private String printable(String value, boolean bold) throws IOException {
    StringBuilder result = new StringBuilder();
    for (int code : (value == null ? "" : value.replace("−", "-").replace("√", "sqrt ")).codePoints().toArray()) {
      String character = Character.isISOControl(code) ? " " : new String(Character.toChars(code));
      try {
        font(bold).getStringWidth(character);
        result.append(character);
      } catch (IllegalArgumentException _) {
        result.append('?');
      }
    }
    return result.toString();
  }

  /** Wraps words and exceptionally long words without cutting away any text. */
  public List<String> wrap(String value, float width, float size, boolean bold) throws IOException {
    List<String> lines = new ArrayList<>();
    for (String paragraph : (value == null ? "" : value).split("\\R", -1)) {
      String line = "";
      for (String word : paragraph.split("\\s+")) {
        String candidate = line.isEmpty() ? word : line + " " + word;
        if (measure(candidate, size, bold) <= width) {
          line = candidate;
          continue;
        }
        if (!line.isEmpty()) {
          lines.add(line);
        }
        line = "";
        for (int code : word.codePoints().toArray()) {
          String character = new String(Character.toChars(code));
          if (measure(line + character, size, bold) > width && !line.isEmpty()) {
            lines.add(line);
            line = "";
          }
          line += character;
        }
      }
      lines.add(line);
    }
    return lines;
  }

  public void hairline(float x, float baseline, float width) throws IOException {
    stream.setStrokingColor(new Color(180, 191, 200));
    stream.setLineWidth(0.5f);
    stream.moveTo(x, baseline);
    stream.lineTo(x + width, baseline);
    stream.stroke();
  }

  public void shadedBar(float x, float bottom, float width, float height) throws IOException {
    stream.setNonStrokingColor(PALE);
    stream.addRect(x, bottom, width, height);
    stream.fill();
  }

  public void contentsRow(PerformanceReportSection section) throws IOException {
    contents.add(new TocRow(section, page, y));
    text(formatter.text(section.name()), MARGIN, y, 10, false);
    y -= 23;
  }

  private static PDPageXYZDestination destination(PDPage page, float y) {
    PDPageXYZDestination destination = new PDPageXYZDestination();
    destination.setPage(page);
    destination.setTop((int) y + 18);
    return destination;
  }

  /** Completes page numbers and clickable contents using the same font subsets, then closes the document. */
  public byte[] toByteArray() throws IOException {
    if (stream != null) {
      stream.close();
      stream = null;
    }
    for (int i = 0; i < document.getNumberOfPages(); i++) {
      page = document.getPage(i);
      try (PDPageContentStream appendix = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND,
          true, true)) {
        stream = appendix;
        hairline(MARGIN, 49, width());
        textWrapped(sender, MARGIN, 36, width() * 0.34f, 7, false);
        text(created, MARGIN + width() * 0.36f, 36, 7, false);
        textRight(formatter.text("gt.report.page", i + 1, document.getNumberOfPages()), MARGIN + width(), 36, 8, false);
        for (TocRow row : contents) {
          if (row.page().getCOSObject() == page.getCOSObject()) {
            fillContents(row);
          }
        }
      }
      stream = null;
    }
    outline.openNode();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    document.save(bytes);
    document.close();
    return bytes.toByteArray();
  }

  private void fillContents(TocRow row) throws IOException {
    Anchor anchor = anchors.get(row.section());
    if (anchor == null) {
      return;
    }
    textRight(Integer.toString(anchor.number()), MARGIN + width(), row.y(), 10, false);
    PDAnnotationLink link = new PDAnnotationLink();
    link.setRectangle(new PDRectangle(MARGIN, row.y() - 4, width(), 18));
    PDActionGoTo action = new PDActionGoTo();
    action.setDestination(destination(anchor.page(), anchor.y()));
    link.setAction(action);
    COSArray border = new COSArray();
    border.add(COSInteger.ZERO);
    border.add(COSInteger.ZERO);
    border.add(COSInteger.ZERO);
    link.setBorder(border);
    page.getAnnotations().add(link);
  }

  @Override
  public void close() throws IOException {
    if (stream != null) {
      stream.close();
      stream = null;
    }
    document.close();
  }
}
