package grafioschtrader.report.pdf;

import java.io.IOException;

import org.springframework.stereotype.Component;

import grafioschtrader.types.PerformanceReportSection;

/** Envelope-window cover with a reserved, clickable contents block. */
@Component
public class CoverRenderer implements SectionRenderer {
  public PerformanceReportSection section() {
    return PerformanceReportSection.COVER;
  }

  public void render(ReportData data, PdfDocumentWriter writer, ReportFormatter f) throws IOException {
    writer.beginSection(section(), PdfDocumentWriter.Orientation.PORTRAIT);
    // DIN 5008 recipient window: x=20 mm, top=45 mm, width=85 mm, height=45 mm.
    writer.address(data.request().recipient, 56.7f, 704, 241, 10);
    writer.address(data.request().sender, 337, 787, 218, 9);
    float height = writer.textWrapped(data.title(f), PdfDocumentWriter.MARGIN, 547, writer.width(), 23, true);
    writer.y(547 - height - 16);
    ReportFacts.scope(data, writer, f);
    writer.labelValueRow(f.text("gt.report.created"), f.date(data.created().toLocalDate()));
    writer.heading(f.text("gt.report.contents"));
    for (PerformanceReportSection selected : PerformanceReportSection.values()) {
      if (selected != section() && data.request().sections.contains(selected)) {
        writer.contentsRow(selected);
      }
    }
  }
}
