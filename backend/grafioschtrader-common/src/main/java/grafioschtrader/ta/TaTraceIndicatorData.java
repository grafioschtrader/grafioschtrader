package grafioschtrader.ta;

/**
 * Single trace for a chart. Some technical indicators may create one or more
 * instances of this class.
 *
 * @author Hugo Graf
 *
 */
public class TaTraceIndicatorData {
  public TaIndicators taIndicator;
  public String traceName;
  public Integer period;

  public TaIndicatorData[] taIndicatorData;

  public TaTraceIndicatorData(TaIndicators taIndicator, String traceName, Integer period,
      TaIndicatorData[] taIndicatorData) {
    this.taIndicator = taIndicator;
    this.traceName = traceName;
    this.period = period;
    this.taIndicatorData = taIndicatorData;
  }

}
