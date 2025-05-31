package grafioschtrader.ta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Represents a single data trace for a technical indicator to be displayed on a chart.
    Some technical indicators might produce multiple traces (e.g., MACD which has a MACD line, a signal line, and a histogram).
    Each trace corresponds to a specific calculation or component of an indicator.""")
public class TaTraceIndicatorData {
  @Schema(description = "The specific technical indicator this trace belongs to (e.g., SMA, EMA). Defines the type of calculation performed.")
  public TaIndicators taIndicator;

  @Schema(description = "It is the name of the enum as specified, therefore no NLS")
  public String traceName;

  @Schema(description = """
      The calculation period (number of data points) used to compute this indicator trace, if applicable.
      For example, for an SMA(20), the period would be 20. May be null for indicators that do not use a period or for sub-traces not directly tied to the main period.""")
  public Integer period;

  @Schema(description = """
      An array of individual data points for this indicator trace.
      Each entry typically contains a date and the calculated indicator value for that date.""")
  public TaIndicatorData[] taIndicatorData;

  public TaTraceIndicatorData(TaIndicators taIndicator, String traceName, Integer period,
      TaIndicatorData[] taIndicatorData) {
    this.taIndicator = taIndicator;
    this.traceName = traceName;
    this.period = period;
    this.taIndicatorData = taIndicatorData;
  }

}
