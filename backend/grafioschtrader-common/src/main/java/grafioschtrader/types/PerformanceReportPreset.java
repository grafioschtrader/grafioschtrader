package grafioschtrader.types;

import static grafioschtrader.types.PerformanceReportSection.*;

import java.util.Set;

/** Starting selections offered by the server; unavailable presets are not offered. */
public enum PerformanceReportPreset {
  SHORT_REPORT(PERFORMANCE_SUMMARY, PERFORMANCE_CHARTS, GLOSSARY),
  PERFORMANCE_DETAIL(COVER, PERFORMANCE_SUMMARY, PERFORMANCE_CHARTS, ANNUAL_RETURNS, PERIOD_WINDOWS, RISK_COST_METRICS,
      GLOSSARY),
  STATEMENT_OF_ASSETS(COVER, HOLDINGS, ALLOCATION, GLOSSARY),
  INCOME_AND_COSTS(COVER, PERFORMANCE_SUMMARY, INCOME_COSTS, GLOSSARY), CUSTOM;

  private final Set<PerformanceReportSection> sections;

  PerformanceReportPreset(PerformanceReportSection... sections) {
    this.sections = Set.of(sections);
  }

  public Set<PerformanceReportSection> getSections() {
    return sections;
  }

  public boolean isAvailable() {
    return sections.stream().allMatch(PerformanceReportSection::isAvailable);
  }
}
