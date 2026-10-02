package grafioschtrader.report.pdf;

import static grafioschtrader.report.pdf.HoldingsData.money;
import static grafioschtrader.report.pdf.HoldingsData.weight;
import static grafioschtrader.report.pdf.PdfTable.Alignment.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.PerformanceReportSection;

/** Allocation uses signed account values, including cash, from the same snapshot as the statement. */
@Component
public class AllocationRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.ALLOCATION;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.LANDSCAPE, 430);
    writer.paragraph(f.text("gt.report.reporting.date") + ": " + f.date(data.request().valuationDate()));
    writer.paragraph(f.text("gt.report.allocation.method"));
    if (data.holdings().total() <= 0) {
      writer.paragraph(f.text("gt.report.weights.unavailable"));
    }
    Map<String, Double> classes = new LinkedHashMap<>();
    data.holdings().assetClasses().forEach((type, value) -> classes.put(f.text(type.name()), value));
    block(f.text("gt.report.by.asset.class"), classes, data, writer, f);
    block(f.text("gt.report.by.currency"), data.holdings().currencies(), data, writer, f);
    matrix(data, writer, f);
  }

  private void block(String title, Map<String, Double> groups, ReportData data, PdfDocumentWriter writer,
      ReportFormatter f) throws IOException {
    if (groups.isEmpty()) {
      return;
    }
    boolean complete = groups.values().stream().allMatch(Double::isFinite);
    boolean donut = complete && data.holdings().total() > 0 && groups.values().stream().allMatch(v -> v >= 0);
    var slices = donut ? slices(groups, f.text("gt.report.other")) : List.<PdfChartDrawer.Slice>of();
    var entries = new ArrayList<>(groups.entrySet());
    // A bounded block keeps the chart beside its table even with many currencies and long translated names.
    for (int offset = 0; offset < entries.size(); offset += 6) {
      writer.ensureSpace(340);
      writer.heading(title + (offset == 0 ? "" : " · " + f.text("gt.report.continued")));
      float top = writer.y();
      var chunk = entries.subList(offset, Math.min(entries.size(), offset + 6));
      List<PdfTable.Row> rows = new ArrayList<>();
      for (var entry : chunk) {
        String label = (entries.indexOf(entry) + 1) + "  " + entry.getKey();
        if (donut) {
          int index = sliceIndex(entry, slices);
          label = (entry.getValue() == 0 ? "–" : String.valueOf(index + 1)) + "  " + entry.getKey();
        }
        rows.add(new PdfTable.Row(List.of(label, money(entry.getValue(), data, f), weight(entry.getValue(), data, f))));
      }
      if (offset + 6 >= entries.size()) {
        rows.add(new PdfTable.Row(List.of(f.text("gt.report.grand.total"), money(data.holdings().total(), data, f),
            weight(data.holdings().total(), data, f)), PdfTable.Kind.TOTAL));
      }
      var columns = List.of(new PdfTable.Column(title, 2.6f, LEFT, true),
          new PdfTable.Column(f.text("gt.report.value.mc"), 1.5f, RIGHT, false),
          new PdfTable.Column(f.text("gt.report.weight"), 1.2f, RIGHT, false));
      float tableWidth = writer.width() * .51f;
      new PdfTable(writer, columns, 8, f.text("gt.report.continued"), PdfDocumentWriter.MARGIN, tableWidth).draw(rows);
      float bottom = writer.y();
      var rect = new PdfChartDrawer.Rect(PdfDocumentWriter.MARGIN + tableWidth + 24, top - 250,
          writer.width() - tableWidth - 24, 240);
      if (donut) {
        PdfChartDrawer.donutChartNumbered(writer, rect, slices,
            chunk.stream().map(e -> sliceIndex(e, slices)).collect(java.util.stream.Collectors.toSet()));
      } else if (complete) {
        PdfChartDrawer.horizontalBarChart(writer, rect,
            chunk.stream().map(e -> String.valueOf(entries.indexOf(e) + 1)).toList(),
            chunk.stream().map(Map.Entry::getValue).toList(), v -> money(v, data, f));
      }
      writer.y(complete ? Math.min(bottom, top - 270) : bottom);
      if (donut && slices.size() < groups.size()) {
        writer.paragraph(f.text("gt.report.other.note", slices.size()));
      }
    }
  }

  /** Merge only chart slices, using unrounded weights; the accompanying table retains every group. */
  static List<PdfChartDrawer.Slice> slices(Map<String, Double> groups, String other) {
    double total = groups.values().stream().mapToDouble(Double::doubleValue).sum();
    List<PdfChartDrawer.Slice> result = new ArrayList<>();
    double small = 0;
    for (var entry : groups.entrySet()) {
      if (entry.getValue() / total < .005) {
        small += entry.getValue();
      } else {
        result.add(new PdfChartDrawer.Slice(entry.getKey(), entry.getValue()));
      }
    }
    if (small > 0) {
      result.add(new PdfChartDrawer.Slice(other, small));
    }
    return result;
  }

  private int sliceIndex(Map.Entry<String, Double> entry, List<PdfChartDrawer.Slice> slices) {
    for (int i = 0; i < slices.size(); i++) {
      if (slices.get(i).label().equals(entry.getKey())) {
        return i;
      }
    }
    return slices.size() - 1;
  }

  private void matrix(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    var classes = data.holdings().assetClasses().keySet().stream()
        .filter(t -> data.holdings().currencies().keySet().stream().anyMatch(c -> data.holdings().cell(c, t) != 0))
        .toList();
    var currencies = data.holdings().currencies().keySet().stream()
        .filter(c -> classes.stream().anyMatch(t -> data.holdings().cell(c, t) != 0)).toList();
    // Wide matrices are split into panels instead of shrinking amounts or identifiers below readable size.
    for (int offset = 0; offset < classes.size(); offset += 5) {
      List<AssetclassType> panel = classes.subList(offset, Math.min(classes.size(), offset + 5));
      writer.ensureSpace(180);
      writer.heading(f.text("gt.report.currency.matrix"));
      List<PdfTable.Column> columns = new ArrayList<>();
      columns.add(new PdfTable.Column(f.text("gt.report.currency"), 1, LEFT, true));
      panel.forEach(t -> columns.add(new PdfTable.Column(f.text(t.name()), 1.5f, RIGHT, true)));
      columns.add(new PdfTable.Column(f.text("gt.report.grand.total"), 1.5f, RIGHT, true));
      List<PdfTable.Row> rows = new ArrayList<>();
      for (String currency : currencies) {
        List<String> cells = new ArrayList<>();
        cells.add(currency);
        panel.forEach(t -> cells.add(matrixCell(data.holdings().cell(currency, t), data, f)));
        cells.add(matrixCell(data.holdings().currencies().get(currency), data, f));
        rows.add(new PdfTable.Row(cells));
      }
      List<String> totals = new ArrayList<>();
      totals.add(f.text("gt.report.grand.total"));
      panel.forEach(t -> totals.add(matrixCell(data.holdings().assetClasses().get(t), data, f)));
      totals.add(matrixCell(data.holdings().total(), data, f));
      rows.add(new PdfTable.Row(totals, PdfTable.Kind.TOTAL));
      new PdfTable(writer, columns, 8, f.text("gt.report.continued")).draw(rows);
    }
  }

  private String matrixCell(double value, ReportData data, ReportFormatter f) {
    return money(value, data, f) + "\n" + weight(value, data, f);
  }
}
