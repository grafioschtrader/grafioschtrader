package grafioschtrader.report.pdf;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

/** Vector charts with every valuation preserved, signed bars and explicit data-gap strokes. */
public final class PdfChartDrawer {
  public static final Color[] PALETTE = { new Color(30, 80, 118), new Color(180, 126, 44), new Color(79, 133, 132),
      new Color(107, 71, 123), new Color(125, 149, 72), new Color(190, 108, 97), new Color(112, 122, 135),
      new Color(69, 54, 45) };
  private static final Color POSITIVE = new Color(47, 111, 85);
  private static final Color NEGATIVE = new Color(156, 57, 55);

  public record Rect(float x, float y, float width, float height) {
  }

  public record Series(String label, List<Double> values, boolean dashed, List<Boolean> regular) {
  }

  public record Scale(double min, double max, double step, List<Double> ticks) {
  }

  public record Slice(String label, double value) {
  }

  private PdfChartDrawer() {
  }

  /** Four to six nice ticks, including constant and single-point series. */
  public static Scale scale(List<Double> values, boolean includeZero) {
    double min = values.stream().filter(PdfChartDrawer::finite).mapToDouble(Double::doubleValue).min().orElse(0);
    double max = values.stream().filter(PdfChartDrawer::finite).mapToDouble(Double::doubleValue).max().orElse(0);
    if (min == max) {
      double pad = min == 0 ? 1 : Math.abs(min) * .01;
      min -= pad;
      max += pad;
    }
    if (includeZero) {
      min = Math.min(0, min);
      max = Math.max(0, max);
    }
    double raw = (max - min) / 4;
    double power = Math.pow(10, Math.floor(Math.log10(raw)));
    double bestStep = power;
    double bestScore = Double.POSITIVE_INFINITY;
    for (double factor : new double[] { 1, 2, 2.5, 5, 10 }) {
      double step = factor * power;
      double count = Math.ceil(max / step) - Math.floor(min / step) + 1;
      double score = Math.abs(count - 5) + (count < 4 || count > 6 ? 10 : 0);
      if (score < bestScore) {
        bestScore = score;
        bestStep = step;
      }
    }
    double lower = Math.floor(min / bestStep) * bestStep;
    double upper = Math.ceil(max / bestStep) * bestStep;
    List<Double> ticks = new ArrayList<>();
    for (int i = 0; i <= Math.round((upper - lower) / bestStep); i++) {
      ticks.add(lower + i * bestStep);
    }
    return new Scale(lower, upper, bestStep, List.copyOf(ticks));
  }

  private static boolean finite(Double value) {
    return value != null && Double.isFinite(value);
  }

  private static float y(Rect r, Scale scale, double value) {
    return r.y() + (float) ((value - scale.min()) / (scale.max() - scale.min()) * r.height());
  }

  private static void axes(PdfDocumentWriter writer, Rect r, Scale scale, Function<Double, String> format)
      throws IOException {
    PDPageContentStream stream = writer.stream();
    stream.saveGraphicsState();
    for (double tick : scale.ticks()) {
      float baseline = y(r, scale, tick);
      stream.setStrokingColor(tick == 0 ? Color.GRAY : new Color(220, 225, 230));
      stream.setLineWidth(.5f);
      stream.setLineDashPattern(tick == 0 ? new float[] { 3, 3 } : new float[0], 0);
      stream.moveTo(r.x(), baseline);
      stream.lineTo(r.x() + r.width(), baseline);
      stream.stroke();
      writer.textRight(format.apply(tick), r.x() - 7, baseline - 3, 7, false);
    }
    stream.restoreGraphicsState();
  }

  public static void lineChart(PdfDocumentWriter writer, Rect r, List<LocalDate> dates, List<Series> series,
      Function<Double, String> format, ReportFormatter formatter) throws IOException {
    Scale scale = scale(series.stream().flatMap(s -> s.values().stream()).toList(), false);
    axes(writer, r, scale, format);
    if (dates.isEmpty()) {
      return;
    }
    long span = Math.max(1, ChronoUnit.DAYS.between(dates.getFirst(), dates.getLast()));
    PDPageContentStream stream = writer.stream();
    stream.saveGraphicsState();
    for (int s = 0; s < series.size(); s++) {
      Series line = series.get(s);
      stream.setStrokingColor(PALETTE[s % PALETTE.length]);
      stream.setNonStrokingColor(PALETTE[s % PALETTE.length]);
      stream.setLineWidth(1.4f);
      for (int i = 0; i < dates.size(); i++) {
        Double value = line.values().get(i);
        if (!finite(value)) {
          continue;
        }
        float x = r.x() + (float) ChronoUnit.DAYS.between(dates.getFirst(), dates.get(i)) / span * r.width();
        if (i > 0 && finite(line.values().get(i - 1))) {
          boolean gap = line.regular() != null && !line.regular().get(i);
          stream.setLineDashPattern(gap ? new float[] { 1, 3 } : line.dashed() ? new float[] { 5, 3 } : new float[0],
              0);
          float previousX = r.x()
              + (float) ChronoUnit.DAYS.between(dates.getFirst(), dates.get(i - 1)) / span * r.width();
          stream.moveTo(previousX, y(r, scale, line.values().get(i - 1)));
          stream.lineTo(x, y(r, scale, value));
          stream.stroke();
        } else {
          stream.addRect(x - 2, y(r, scale, value) - 2, 4, 4);
          stream.fill();
        }
      }
      float legendY = r.y() + r.height() + 16 + s * 14;
      stream.setLineDashPattern(line.dashed() ? new float[] { 5, 3 } : new float[0], 0);
      stream.moveTo(r.x(), legendY);
      stream.lineTo(r.x() + 22, legendY);
      stream.stroke();
      writer.text(line.label(), r.x() + 28, legendY - 3, 8, false);
    }
    stream.restoreGraphicsState();
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(span > 60 ? "MMM yy" : "dd MMM",
        formatter.numberFormat());
    LocalDate next = dates.getFirst();
    float lastX = -Float.MAX_VALUE;
    for (LocalDate date : dates) {
      if (!date.isBefore(next)) {
        float x = r.x() + (float) ChronoUnit.DAYS.between(dates.getFirst(), date) / span * r.width();
        String label = date.format(dateFormat);
        if (x - lastX >= 42) {
          writer.text(label, Math.min(x, r.x() + r.width() - writer.measure(label, 7, false)), r.y() - 15, 7, false);
          lastX = x;
        }
        next = span > 60 ? date.withDayOfMonth(1).plusMonths(1) : date.plusWeeks(1);
      }
    }
  }

  public static void barChart(PdfDocumentWriter writer, Rect r, List<String> labels, List<Double> values,
      Function<Double, String> format) throws IOException {
    Scale scale = scale(values, true);
    axes(writer, r, scale, format);
    if (values.isEmpty()) {
      return;
    }
    float slot = r.width() / values.size();
    float zero = y(r, scale, 0);
    PDPageContentStream stream = writer.stream();
    stream.saveGraphicsState();
    float lastLabelEnd = -Float.MAX_VALUE;
    for (int i = 0; i < values.size(); i++) {
      Double value = values.get(i);
      float x = r.x() + slot * i;
      if (finite(value)) {
        float end = y(r, scale, value);
        stream.setNonStrokingColor(value >= 0 ? POSITIVE : NEGATIVE);
        stream.addRect(x + slot * .18f, Math.min(zero, end), slot * .64f, Math.max(.6f, Math.abs(end - zero)));
        stream.fill();
        if (values.size() <= 24) {
          String label = format.apply(value);
          float width = writer.measure(label, 7, false);
          if (width < slot - 2) {
            writer.text(label, x + (slot - width) / 2, value >= 0 ? end + 4 : end - 10, 7, false);
          }
        }
      }
      String label = labels.get(i);
      float labelWidth = writer.measure(label, 7, false);
      if (!label.isEmpty() && x > lastLabelEnd + 4 && x + labelWidth <= r.x() + r.width()) {
        writer.text(label, x, r.y() - 16, 7, false);
        lastLabelEnd = x + labelWidth;
      }
    }
    stream.restoreGraphicsState();
  }

  /** Signed allocation bars; negative values keep their sign and share one zero axis. */
  public static void horizontalBarChart(PdfDocumentWriter writer, Rect r, List<String> labels, List<Double> values,
      Function<Double, String> format) throws IOException {
    if (values.isEmpty()) {
      return;
    }
    Scale scale = scale(values, true);
    float slot = r.height() / values.size();
    float zero = r.x() + (float) (-scale.min() / (scale.max() - scale.min()) * r.width());
    PDPageContentStream stream = writer.stream();
    stream.saveGraphicsState();
    stream.setStrokingColor(Color.GRAY);
    stream.setLineWidth(.5f);
    stream.moveTo(zero, r.y());
    stream.lineTo(zero, r.y() + r.height());
    stream.stroke();
    for (int i = 0; i < values.size(); i++) {
      float baseline = r.y() + r.height() - (i + 1) * slot;
      Double value = values.get(i);
      writer.text(labels.get(i), r.x(), baseline + slot - 9, 8, false);
      if (!finite(value)) {
        continue;
      }
      float end = r.x() + (float) ((value - scale.min()) / (scale.max() - scale.min()) * r.width());
      stream.setNonStrokingColor(value >= 0 ? POSITIVE : NEGATIVE);
      stream.addRect(Math.min(zero, end), baseline + 3, Math.max(.6f, Math.abs(end - zero)), Math.max(2, slot - 17));
      stream.fill();
      writer.textRight(format.apply(value), r.x() + r.width(), baseline + slot - 9, 8, false);
    }
    stream.restoreGraphicsState();
  }

  /** Positive allocations only. Signed or zero-total allocations must use bars instead. */
  public static void donutChart(PdfDocumentWriter writer, Rect r, List<Slice> slices, ReportFormatter formatter)
      throws IOException {
    double total = slices.stream().mapToDouble(Slice::value).sum();
    if (total <= 0 || slices.stream().anyMatch(s -> !Double.isFinite(s.value()) || s.value() < 0)) {
      throw new IllegalArgumentException("A donut requires a positive total and non-negative slices");
    }
    float radius = Math.min(r.height(), r.width() * .45f) / 2;
    float cx = r.x() + radius;
    float cy = r.y() + r.height() / 2;
    double angle = Math.PI / 2;
    PDPageContentStream stream = writer.stream();
    stream.saveGraphicsState();
    for (int i = 0; i < slices.size(); i++) {
      Slice slice = slices.get(i);
      double next = angle + slice.value() / total * 2 * Math.PI;
      Color color = PALETTE[i % PALETTE.length];
      stream.setNonStrokingColor(color);
      stream.moveTo(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
      arc(stream, cx, cy, radius, angle, next);
      stream.lineTo(cx + radius * .6f * (float) Math.cos(next), cy + radius * .6f * (float) Math.sin(next));
      arc(stream, cx, cy, radius * .6f, next, angle);
      stream.closePath();
      stream.fill();
      float legendY = r.y() + r.height() - 16 - i * 20;
      stream.setNonStrokingColor(color);
      stream.addRect(r.x() + radius * 2 + 14, legendY - 2, 8, 8);
      stream.fill();
      writer.text(slice.label() + " " + formatter.percent(slice.value() / total * 100), r.x() + radius * 2 + 28,
          legendY, 8, false);
      angle = next;
    }
    stream.restoreGraphicsState();
  }

  /** Compact allocation donut with numbered swatches referring to the full table alongside it. */
  public static void donutChartNumbered(PdfDocumentWriter writer, Rect r, List<Slice> slices,
      java.util.Set<Integer> legendIndices) throws IOException {
    double total = slices.stream().mapToDouble(Slice::value).sum();
    if (total <= 0 || slices.stream().anyMatch(s -> !Double.isFinite(s.value()) || s.value() < 0)) {
      throw new IllegalArgumentException("A donut requires a positive total and non-negative slices");
    }
    float radius = Math.min(r.width() / 2 - 10, 82);
    float cx = r.x() + r.width() / 2;
    float cy = r.y() + r.height() - radius;
    double angle = Math.PI / 2;
    var stream = writer.stream();
    stream.saveGraphicsState();
    int legend = 0;
    for (int i = 0; i < slices.size(); i++) {
      double next = angle + slices.get(i).value() / total * 2 * Math.PI;
      stream.setNonStrokingColor(PALETTE[i % PALETTE.length]);
      stream.moveTo(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
      arc(stream, cx, cy, radius, angle, next);
      stream.lineTo(cx + radius * .6f * (float) Math.cos(next), cy + radius * .6f * (float) Math.sin(next));
      arc(stream, cx, cy, radius * .6f, next, angle);
      stream.closePath();
      stream.fill();
      if (legendIndices.contains(i)) {
        float x = r.x() + 15 + legend * 45;
        float y = cy - radius - 20;
        stream.addRect(x, y - 2, 8, 8);
        stream.fill();
        writer.text(String.valueOf(i + 1), x + 12, y, 8, false);
        legend++;
      }
      if ((next - angle) > .22) {
        double middle = (angle + next) / 2;
        float labelX = cx + radius * .8f * (float) Math.cos(middle);
        float labelY = cy + radius * .8f * (float) Math.sin(middle);
        stream.setNonStrokingColor(Color.WHITE);
        stream.addRect(labelX - 6, labelY - 5, 15, 12);
        stream.fill();
        writer.text(String.valueOf(i + 1), labelX - 3, labelY - 3, 8, true);
      }
      angle = next;
    }
    stream.restoreGraphicsState();
  }

  private static void arc(PDPageContentStream stream, float cx, float cy, float radius, double start, double end)
      throws IOException {
    int steps = Math.max(1, (int) Math.ceil(Math.abs(end - start) / (Math.PI / 2)));
    for (int i = 0; i < steps; i++) {
      double a = start + (end - start) * i / steps;
      double b = start + (end - start) * (i + 1) / steps;
      double k = 4.0 / 3 * Math.tan((b - a) / 4);
      stream.curveTo(cx + radius * (float) (Math.cos(a) - k * Math.sin(a)),
          cy + radius * (float) (Math.sin(a) + k * Math.cos(a)), cx + radius * (float) (Math.cos(b) + k * Math.sin(b)),
          cy + radius * (float) (Math.sin(b) - k * Math.cos(b)), cx + radius * (float) Math.cos(b),
          cy + radius * (float) Math.sin(b));
    }
  }
}
