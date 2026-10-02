package grafioschtrader.report.pdf;

import java.io.IOException;

import org.springframework.stereotype.Component;

import grafioschtrader.types.PerformanceReportSection;

/** Terms used by the selected content sections only; essential methodology also remains beside the figures. */
@Component
public class GlossaryRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.GLOSSARY;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.PORTRAIT);
    var sections = data.request().sections;
    if (sections.stream().anyMatch(PerformanceReportSection::needsPeriod)) {
      writer.paragraph(f.text("gt.report.glossary.twr"));
    }
    if (sections.contains(PerformanceReportSection.HOLDINGS)
        || sections.contains(PerformanceReportSection.ALLOCATION)) {
      writer.paragraph(f.text("gt.report.holdings.method"));
      writer.paragraph(f.text("gt.report.allocation.method"));
      writer.paragraph(f.text("gt.report.glossary.holdings.markers"));
      if (sections.contains(PerformanceReportSection.HOLDINGS) && data.request().detailColumns) {
        writer.paragraph(f.text("gt.report.holdings.details.note"));
      }
    }
    if (sections.contains(PerformanceReportSection.PERFORMANCE_SUMMARY)
        || sections.contains(PerformanceReportSection.RISK_COST_METRICS)) {
      writer.paragraph(f.text("gt.report.glossary.mwr"));
      writer.paragraph(f.text("gt.report.glossary.result"));
    }
    if (sections.contains(PerformanceReportSection.RISK_COST_METRICS)) {
      writer.paragraph(f.text("gt.report.glossary.risk"));
      writer.paragraph(f.text("gt.report.glossary.fees"));
    }
    if (sections.contains(PerformanceReportSection.INCOME_COSTS)) {
      writer.paragraph(f.text("gt.report.recorded.costs.method"));
      writer.paragraph(f.text("gt.report.recorded.cost.ratio.method"));
      writer.paragraph(f.text("gt.report.contribution.method"));
    }
    if (data.bookings() != null) {
      writer.paragraph(f.text("gt.report.booking.conversion"));
      writer.paragraph(f.text("gt.report.transfer.method"));
    }
    if (sections.stream().anyMatch(PerformanceReportSection::needsPeriod)) {
      writer.paragraph(f.text("gt.report.glossary.markers"));
    }
  }
}
