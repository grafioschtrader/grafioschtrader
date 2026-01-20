package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response wrapper for chart data containing either simple date/close data or full OHLC data depending on data
 * availability. The frontend uses this response to determine whether candlestick/OHLC charts can be displayed.
 */
@Schema(description = """
    Response wrapper for historical quote chart data. Contains a flag indicating OHLC data availability and either
    a list of simple date/close data points or full OHLC data points. The frontend uses the ohlcAvailable flag to
    determine whether to offer candlestick and OHLC chart options.
    """)
public class HistoryquoteChartResponse {

  @Schema(description = """
      Indicates whether OHLC (Open-High-Low-Close) data is available for this security. When true, ohlcList contains
      the data and candlestick/OHLC charts can be rendered. When false, dateCloseList contains simple date/close data
      and only line charts are available.
      """)
  private boolean ohlcAvailable;

  @Schema(description = """
      List of simple date and close price pairs. Populated when ohlcAvailable is false. Used for line chart rendering
      when full OHLC data is not available for the security.
      """)
  private List<HistoryquoteDateClose> dateCloseList;

  @Schema(description = """
      List of full OHLC data points including open, high, low, and close prices. Populated when ohlcAvailable is true.
      Used for candlestick and OHLC chart rendering.
      """)
  private List<HistoryquoteDateOHLC> ohlcList;

  public HistoryquoteChartResponse() {
  }

  /**
   * Constructs a response with OHLC data.
   *
   * @param ohlcList the list of OHLC data points
   */
  public HistoryquoteChartResponse(List<HistoryquoteDateOHLC> ohlcList) {
    this.ohlcAvailable = true;
    this.ohlcList = ohlcList;
    this.dateCloseList = null;
  }

  /**
   * Constructs a response with simple date/close data.
   *
   * @param dateCloseList the list of date/close data points
   * @param ohlcAvailable must be false to indicate OHLC is not available
   */
  public HistoryquoteChartResponse(List<HistoryquoteDateClose> dateCloseList, boolean ohlcAvailable) {
    this.ohlcAvailable = ohlcAvailable;
    this.dateCloseList = dateCloseList;
    this.ohlcList = null;
  }

  public boolean isOhlcAvailable() {
    return ohlcAvailable;
  }

  public void setOhlcAvailable(boolean ohlcAvailable) {
    this.ohlcAvailable = ohlcAvailable;
  }

  public List<HistoryquoteDateClose> getDateCloseList() {
    return dateCloseList;
  }

  public void setDateCloseList(List<HistoryquoteDateClose> dateCloseList) {
    this.dateCloseList = dateCloseList;
  }

  public List<HistoryquoteDateOHLC> getOhlcList() {
    return ohlcList;
  }

  public void setOhlcList(List<HistoryquoteDateOHLC> ohlcList) {
    this.ohlcList = ohlcList;
  }
}
