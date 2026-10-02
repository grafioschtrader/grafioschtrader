package grafioschtrader.report.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Tables with measured rows, repeated headings and explicit failure for an amount that cannot fit. */
public final class PdfTable {
  public enum Alignment {
    LEFT, RIGHT
  }

  public enum Kind {
    NORMAL, GROUP, SUBTOTAL, TOTAL
  }

  public record Column(String header, float weight, Alignment alignment, boolean wrap) {
  }

  public record Row(List<String> cells, Kind kind) {
    public Row(List<String> cells) {
      this(cells, Kind.NORMAL);
    }
  }

  private static final float PADDING = 5;
  private final PdfDocumentWriter writer;
  private final List<Column> columns;
  private final float[] widths;
  private final float size;
  private final String continued;
  private final float x;
  private final float tableWidth;

  public PdfTable(PdfDocumentWriter writer, List<Column> columns, float size, String continued) {
    this(writer, columns, size, continued, PdfDocumentWriter.MARGIN, writer.width());
  }

  public PdfTable(PdfDocumentWriter writer, List<Column> columns, float size, String continued, float x, float width) {
    if (size < 7) {
      throw new IllegalArgumentException("Report tables must remain legible");
    }
    this.writer = writer;
    this.columns = List.copyOf(columns);
    this.size = size;
    this.continued = continued;
    this.x = x;
    this.tableWidth = width;
    widths = new float[columns.size()];
    double total = columns.stream().mapToDouble(Column::weight).sum();
    for (int i = 0; i < widths.length; i++) {
      widths[i] = (float) (width * columns.get(i).weight() / total);
    }
  }

  public void draw(List<Row> rows) throws IOException {
    List<List<String>> header = lines(columns.stream().map(Column::header).toList(), true, true);
    float headerHeight = height(header);
    writer.ensureSpace(headerHeight + (rows.isEmpty() ? 0 : Math.min(height(lines(rows.getFirst())), 45)));
    paint(header, Kind.TOTAL, 0, count(header));
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      List<List<String>> cells = lines(row);
      float needed = height(cells);
      if (i + 1 < rows.size() && (row.kind() == Kind.GROUP || rows.get(i + 1).kind() == Kind.SUBTOTAL)) {
        needed += height(lines(rows.get(i + 1)));
      }
      float pageCapacity = writer.top() - PdfDocumentWriter.BOTTOM - headerHeight;
      if (needed > writer.available() && needed <= pageCapacity) {
        writer.ensureSpace(writer.available() + 1);
        paint(header, Kind.TOTAL, 0, count(header));
      }
      int offset = 0;
      int length = count(cells);
      // A row taller than a full page is the sole permitted split; repeat the header and mark the continuation.
      while (offset < length) {
        int fitting = (int) ((writer.available() - 2 * PADDING) / (size * 1.4f));
        if (fitting < 1 || (offset == 0 && length > fitting && height(cells) <= pageCapacity)) {
          writer.ensureSpace(writer.available() + 1);
          paint(header, Kind.TOTAL, 0, count(header));
          fitting = (int) ((writer.available() - 2 * PADDING) / (size * 1.4f));
        }
        int end = Math.min(length, offset + fitting);
        paint(cells, row.kind(), offset, end);
        offset = end;
        if (offset < length) {
          writer.ensureSpace(writer.available() + 1);
          paint(header, Kind.TOTAL, 0, count(header));
          writer.paragraph(continued);
        }
      }
    }
    writer.y(writer.y() - 12);
  }

  private List<List<String>> lines(Row row) throws IOException {
    return lines(row.cells(), row.kind() != Kind.NORMAL, false);
  }

  private List<List<String>> lines(List<String> values, boolean bold, boolean header) throws IOException {
    if (values.size() != columns.size()) {
      throw new IllegalArgumentException("Table column count mismatch");
    }
    List<List<String>> result = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      String value = values.get(i) == null ? "" : values.get(i);
      float width = widths[i] - 2 * PADDING;
      if (header || columns.get(i).wrap()) {
        result.add(writer.wrap(value, width, size, bold));
      } else {
        if (writer.measure(value, size, bold) > width) {
          throw new IOException("Report table cell exceeds column width");
        }
        result.add(List.of(value));
      }
    }
    return result;
  }

  private int count(List<List<String>> cells) {
    return cells.stream().mapToInt(List::size).max().orElse(1);
  }

  private float height(List<List<String>> cells) {
    return count(cells) * size * 1.4f + 2 * PADDING;
  }

  private void paint(List<List<String>> cells, Kind kind, int from, int to) throws IOException {
    float height = (to - from) * size * 1.4f + 2 * PADDING;
    float top = writer.y();
    if (kind == Kind.TOTAL || kind == Kind.GROUP) {
      writer.shadedBar(x, top - height, tableWidth, height);
    }
    if (kind == Kind.SUBTOTAL || kind == Kind.TOTAL) {
      writer.hairline(x, top, tableWidth);
    }
    float x = this.x;
    for (int column = 0; column < cells.size(); column++) {
      for (int line = from; line < Math.min(to, cells.get(column).size()); line++) {
        float baseline = top - PADDING - size - (line - from) * size * 1.4f;
        String value = cells.get(column).get(line);
        if (columns.get(column).alignment() == Alignment.RIGHT) {
          writer.textRight(value, x + widths[column] - PADDING, baseline, size, kind != Kind.NORMAL);
        } else {
          writer.text(value, x + PADDING, baseline, size, kind != Kind.NORMAL);
        }
      }
      x += widths[column];
    }
    writer.y(top - height);
  }
}
