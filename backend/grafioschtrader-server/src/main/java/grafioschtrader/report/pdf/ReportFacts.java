package grafioschtrader.report.pdf;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Mandatory context and data-status block shared by every preset. */
final class ReportFacts {
  private ReportFacts() {
  }

  static void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.startContent();
    writer.ensureSpace(180);
    writer.heading(f.text("gt.report.facts"));
    scope(data, writer, f);
    if (!data.portfolios().isEmpty()) {
      writer.paragraph(String.join(", ", data.portfolios()));
    }
    writer.labelValueRow(f.text("gt.report.created"),
        data.created().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", f.numberFormat())));
    writer.labelValueRow(f.text("gt.report.tax.setting"),
        f.text(data.excludeDivTax() ? "gt.report.yes" : "gt.report.no"));
    writer.labelValueRow(f.text("gt.report.fx.setting"),
        f.text(data.feeInterestFxAtCutOffDate() ? "gt.report.yes" : "gt.report.no"));
    if (data.holdings() != null) {
      for (var position : data.holdings().positions()) {
        if (position.priceMissing) {
          writer.paragraph(f.text("gt.report.price.missing", position.getSecurity().getName()));
        } else if (!HoldingsData.cash(position) && position.closeDate != null
            && position.closeDate.isBefore(data.request().valuationDate())) {
          writer
              .paragraph(f.text("gt.report.price.older", position.getSecurity().getName(), f.date(position.closeDate)));
        }
      }
    }
    if (data.openingHoldings() != null) {
      for (var position : data.openingHoldings().positions()) {
        if (position.priceMissing || !HoldingsData.cash(position) && position.closeDate != null
            && position.closeDate.isBefore(data.request().dateFrom)) {
          writer.paragraph(f.text("gt.report.opening.price.status", position.getSecurity().getName(),
              position.priceMissing ? "n/a" : f.date(position.closeDate)));
        }
      }
    }
    if (data.bookings() != null && data.bookings().missingRates() > 0) {
      writer.paragraph(f.text("gt.report.missing.booking.rates", data.bookings().missingRates()));
    }
    if (data.period() == null) {
      return;
    }
    long missing = inPeriod(data.tradingDays().missingQuoteDays, data);
    long holidays = inPeriod(data.tradingDays().allHolydays, data);
    if (missing > 0) {
      writer.paragraph(f.text("gt.report.missing.days", missing));
    }
    if (holidays > 0) {
      writer.paragraph(f.text("gt.report.holiday.days", holidays));
    }
    var metrics = data.period().getMetrics();
    if (metrics == null) {
      writer.paragraph(f.text("gt.report.no.holdings"));
      return;
    }
    if (metrics.gapIntervals() > 0) {
      writer.paragraph(f.text("gt.report.gaps", metrics.gapIntervals()));
    }
    if (metrics.feesWithoutRate() > 0) {
      writer.paragraph(f.text("gt.report.missing.fees", metrics.feesWithoutRate()));
    }
    if (metrics.mwrPercent() == null) {
      writer.labelValueRow(f.text("MWR_STATUS"), f.text(metrics.mwrStatus().name()));
    }
  }

  static void scope(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.labelValueRow(f.text("gt.report.scope"), data.scope());
    writer.paragraph(f.text("gt.report.valued.in", data.currency()));
    if (!data.request().hasPeriod()) {
      writer.labelValueRow(f.text("gt.report.reporting.date"), f.date(data.request().valuationDate()));
      return;
    }
    writer.labelValueRow(f.text("gt.report.valuation.base"), f.date(data.request().dateFrom));
    writer.labelValueRow(f.text("gt.report.booking.range"),
        f.date(data.request().dateFrom.plusDays(1)) + " – " + f.date(data.request().dateTo));
    writer.labelValueRow(f.text("period.split"), f.text(data.request().periodSplit.name()));
  }

  private static long inPeriod(Set<LocalDate> dates, ReportData data) {
    return dates.stream().filter(d -> d.isAfter(data.request().dateFrom) && !d.isAfter(data.request().dateTo)).count();
  }
}
