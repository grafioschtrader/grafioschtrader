package grafioschtrader.report.pdf;

import static grafioschtrader.report.pdf.HoldingsData.money;
import static grafioschtrader.report.pdf.HoldingsData.weight;
import static grafioschtrader.report.pdf.PdfTable.Alignment.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.PerformanceReportSection;

/** Reporting-date positions; exposure is a memo amount and never part of equity or allocation. */
@Component
public class HoldingsRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.HOLDINGS;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.LANDSCAPE, 220);
    writer.paragraph(f.text("gt.report.reporting.date") + ": " + f.date(data.request().valuationDate()));
    writer.paragraph(f.text("gt.report.holdings.method"));
    if (data.request().detailColumns) {
      writer.paragraph(f.text("gt.report.holdings.details.note"));
    }
    if (data.holdings().total() <= 0) {
      writer.paragraph(f.text("gt.report.weights.unavailable"));
    }
    securities(data, writer, f);
    cash(data, writer, f);
    writer.labelValueRow(f.text("gt.report.grand.total"),
        money(data.holdings().total(), data, f) + " " + data.currency());
    if (data.period() != null) {
      double difference = data.holdings().total() - data.period().getLastDayTotals().getTotalBalanceMC();
      if (Math.abs(difference) > Math.pow(10, -f.currencyPrecision().apply(data.currency()))) {
        writer.paragraph(f.text("gt.report.holdings.reconciliation", money(difference, data, f), data.currency()));
      }
    }
    writer.heading(f.text("gt.report.exchange.rates"));
    for (String currency : data.holdings().currencies().keySet()) {
      if (!currency.equals(data.currency())) {
        Double rate = data.holdings().exchangeRates().get(currency);
        writer.paragraph("1 " + currency + " = " + (rate == null ? "n/a" : f.rate(rate)) + " " + data.currency());
      }
    }
  }

  private void securities(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    var positions = data.holdings().positions().stream().filter(p -> !HoldingsData.cash(p)).toList();
    if (positions.isEmpty()) {
      return;
    }
    boolean exposure = positions.stream().anyMatch(p -> p.getSecurity().isMarginInstrument());
    boolean details = data.request().detailColumns;
    var columns = new ArrayList<>(List.of(column(f, "gt.report.units", 1.15f, RIGHT),
        column(f, "gt.report.instrument", 2.8f, LEFT), column(f, "gt.report.currency", .65f, LEFT),
        column(f, "gt.report.price.date", 1.5f, RIGHT), column(f, "gt.report.position.value", 1.5f, RIGHT),
        column(f, "gt.report.value.mc", 1.5f, RIGHT), column(f, "gt.report.weight", 1.05f, RIGHT)));
    if (exposure) {
      columns.add(column(f, "gt.report.exposure", 1.5f, RIGHT));
    }
    if (details) {
      columns.add(column(f, "gt.report.since.opening.price", 1.5f, RIGHT));
      columns.add(column(f, "gt.report.since.opening.currency", 1.5f, RIGHT));
    }
    List<PdfTable.Row> rows = new ArrayList<>();
    for (AssetclassType type : data.holdings().assetClasses().keySet()) {
      var group = positions.stream().filter(p -> p.getSecurity().getAssetClass().getCategoryType() == type).toList();
      if (group.isEmpty()) {
        continue;
      }
      var heading = new ArrayList<>(Collections.nCopies(columns.size(), ""));
      heading.set(1, f.text(type.name()));
      rows.add(new PdfTable.Row(heading, PdfTable.Kind.GROUP));
      for (var p : group) {
        rows.add(securityRow(p, data, f, exposure, details));
      }
      rows.add(totalRow(f.text("gt.report.subtotal"), group, columns.size(), data, f, PdfTable.Kind.SUBTOTAL));
    }
    rows.add(totalRow(f.text("gt.report.securities.total"), positions, columns.size(), data, f, PdfTable.Kind.TOTAL));
    new PdfTable(writer, columns, 7, f.text("gt.report.continued")).draw(rows);
  }

  private PdfTable.Row securityRow(SecurityPositionSummary p, ReportData data, ReportFormatter f, boolean exposure,
      boolean details) {
    var security = p.getSecurity();
    boolean older = p.closeDate != null && p.closeDate.isBefore(data.request().valuationDate());
    String price = p.priceMissing || p.closePrice == null ? "n/a"
        : f.price(p.closePrice) + (security.isBondDirectInvestment() ? " %" : "") + (older ? "*" : "");
    var cells = new ArrayList<>(
        List.of(f.units(p.units), security.getName() + (security.getIsin() == null ? "" : "\n" + security.getIsin()),
            security.getCurrency(), price + "\n" + f.date(p.closeDate),
            p.priceMissing ? "n/a" : f.money(p.accountValueSecurity, security.getCurrency()),
            money(HoldingsData.value(p), data, f), weight(HoldingsData.value(p), data, f)));
    if (exposure) {
      cells.add(security.isMarginInstrument() ? money(p.priceMissing ? Double.NaN : p.valueSecurityMC, data, f) : "");
    }
    if (details) {
      cells.add(money(p.priceMissing ? Double.NaN : p.gainLossSecurityMC, data, f));
      cells.add(money(p.priceMissing ? Double.NaN : p.gainLossCurrencyMC, data, f));
    }
    return new PdfTable.Row(cells);
  }

  private PdfTable.Row totalRow(String title, List<SecurityPositionSummary> positions, int size, ReportData data,
      ReportFormatter f, PdfTable.Kind kind) {
    double value = positions.stream().mapToDouble(HoldingsData::value).sum();
    var cells = new ArrayList<>(Collections.nCopies(size, ""));
    cells.set(1, title);
    cells.set(5, money(value, data, f));
    cells.set(6, weight(value, data, f));
    return new PdfTable.Row(cells, kind);
  }

  private void cash(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.ensureSpace(160);
    writer.heading(f.text("gt.report.cash"));
    var columns = List.of(column(f, "gt.report.account", 3, LEFT), column(f, "gt.report.currency", 1, LEFT),
        column(f, "gt.report.balance", 2, RIGHT), column(f, "gt.report.exchange.rate", 2, RIGHT),
        column(f, "gt.report.value.mc", 2, RIGHT), column(f, "gt.report.weight", 1.5f, RIGHT));
    var positions = data.holdings().positions().stream().filter(HoldingsData::cash).toList();
    List<PdfTable.Row> rows = new ArrayList<>();
    for (var p : positions) {
      String currency = p.getSecurity().getCurrency();
      Double rate = data.holdings().exchangeRates().get(currency);
      rows.add(new PdfTable.Row(List.of(p.getSecurity().getName(), currency, f.money(p.accountValueSecurity, currency),
          rate == null ? "n/a" : f.rate(rate), money(HoldingsData.value(p), data, f),
          weight(HoldingsData.value(p), data, f))));
    }
    double total = positions.stream().mapToDouble(HoldingsData::value).sum();
    rows.add(new PdfTable.Row(
        List.of(f.text("gt.report.cash.total"), "", "", "", money(total, data, f), weight(total, data, f)),
        PdfTable.Kind.TOTAL));
    new PdfTable(writer, columns, 9, f.text("gt.report.continued")).draw(rows);
  }

  private PdfTable.Column column(ReportFormatter f, String key, float width, PdfTable.Alignment alignment) {
    return new PdfTable.Column(f.text(key), width, alignment, alignment == LEFT || key.equals("gt.report.price.date"));
  }
}
