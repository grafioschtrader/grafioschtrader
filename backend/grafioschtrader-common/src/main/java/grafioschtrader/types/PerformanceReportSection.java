package grafioschtrader.types;

/** Document order and delivery boundary of the selectable PDF sections. */
public enum PerformanceReportSection {
  COVER(1, false), PERFORMANCE_SUMMARY(1, true), PERFORMANCE_CHARTS(1, true), ANNUAL_RETURNS(1, true),
  PERIOD_WINDOWS(1, true), RISK_COST_METRICS(1, true), HOLDINGS(2, false), ALLOCATION(2, false), INCOME_COSTS(3, true),
  TRANSACTIONS(3, true), GLOSSARY(1, false);

  private final int stage;
  private final boolean period;

  PerformanceReportSection(int stage, boolean period) {
    this.stage = stage;
    this.period = period;
  }

  public boolean isAvailable() {
    return stage <= 3;
  }

  public boolean needsPeriod() {
    return period;
  }

  public boolean isContent() {
    return this != COVER && this != GLOSSARY;
  }
}
