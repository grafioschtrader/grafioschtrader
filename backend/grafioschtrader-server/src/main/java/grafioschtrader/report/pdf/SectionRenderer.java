package grafioschtrader.report.pdf;

import java.io.IOException;

import grafioschtrader.types.PerformanceReportSection;

/** A single, independently extensible document section. */
public interface SectionRenderer {
  PerformanceReportSection section();

  void render(ReportData data, PdfDocumentWriter writer, ReportFormatter formatter) throws IOException;
}
