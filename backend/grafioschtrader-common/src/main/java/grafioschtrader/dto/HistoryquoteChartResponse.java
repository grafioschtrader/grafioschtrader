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

  @Schema(description = """
      Indicates whether volume data is available for this security. When true, volume values in ohlcList are populated
      and the volume subchart can be displayed.
      """)
  private boolean volumeAvailable;

  public HistoryquoteChartResponse() {
  }

  /**
   * Creates a response with OHLC data.
   *
   * @param ohlcList the list of OHLC data points
   * @return a new HistoryquoteChartResponse with OHLC data
   */
  public static HistoryquoteChartResponse ofOhlc(List<HistoryquoteDateOHLC> ohlcList) {
    HistoryquoteChartResponse response = new HistoryquoteChartResponse();
    response.ohlcAvailable = true;
    response.ohlcList = ohlcList;
    response.dateCloseList = null;
    return response;
  }

  /**
   * Creates a response with simple date/close data.
   *
   * @param dateCloseList the list of date/close data points
   * @return a new HistoryquoteChartResponse with date/close data
   */
  public static HistoryquoteChartResponse ofDateClose(List<HistoryquoteDateClose> dateCloseList) {
    HistoryquoteChartResponse response = new HistoryquoteChartResponse();
    response.ohlcAvailable = false;
    response.dateCloseList = dateCloseList;
    response.ohlcList = null;
    return response;
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

  public boolean isVolumeAvailable() {
    return volumeAvailable;
  }

  public void setVolumeAvailable(boolean volumeAvailable) {
    this.volumeAvailable = volumeAvailable;
  }
}
