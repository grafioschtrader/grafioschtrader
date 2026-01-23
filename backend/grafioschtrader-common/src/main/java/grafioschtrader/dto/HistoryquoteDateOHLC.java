package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data transfer object for OHLC (Open-High-Low-Close) historical quote data used in candlestick and OHLC charts.
 * This DTO extends the basic date/close structure with open, high, and low price fields needed for advanced chart
 * visualization.
 */
@Schema(description = """
    Data transfer object for OHLC (Open-High-Low-Close) historical quote data. Contains the four price points needed
    for candlestick and OHLC chart visualization: opening price, highest price, lowest price, and closing price for
    a given trading date.
    """)
public class HistoryquoteDateOHLC {

  @Schema(description = "Trading date for this OHLC data point")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;

  @Schema(description = "Opening price of the day")
  public Double open;

  @Schema(description = "Highest price of the day")
  public Double high;

  @Schema(description = "Lowest price of the day")
  public Double low;

  @Schema(description = "Closing price of the day")
  public Double close;

  @Schema(description = "Trading volume for the day")
  public Long volume;

  public HistoryquoteDateOHLC() {
  }

  /**
   * Constructs a HistoryquoteDateOHLC from java.sql.Date and price values.
   *
   * @param date   the trading date (as java.sql.Date from database)
   * @param open   the opening price
   * @param high   the highest price
   * @param low    the lowest price
   * @param close  the closing price
   * @param volume the trading volume
   */
  public HistoryquoteDateOHLC(Date date, Double open, Double high, Double low, Double close, Long volume) {
    this.date = ((java.sql.Date) date).toLocalDate();
    this.open = open;
    this.high = high;
    this.low = low;
    this.close = close;
    this.volume = volume;
  }

  /**
   * Constructs a HistoryquoteDateOHLC from LocalDate and price values.
   *
   * @param localDate the trading date
   * @param open      the opening price
   * @param high      the highest price
   * @param low       the lowest price
   * @param close     the closing price
   * @param volume    the trading volume
   */
  public HistoryquoteDateOHLC(LocalDate localDate, Double open, Double high, Double low, Double close, Long volume) {
    this.date = localDate;
    this.open = open;
    this.high = high;
    this.low = low;
    this.close = close;
    this.volume = volume;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public Double getOpen() {
    return open;
  }

  public void setOpen(Double open) {
    this.open = open;
  }

  public Double getHigh() {
    return high;
  }

  public void setHigh(Double high) {
    this.high = high;
  }

  public Double getLow() {
    return low;
  }

  public void setLow(Double low) {
    this.low = low;
  }

  public Double getClose() {
    return close;
  }

  public void setClose(Double close) {
    this.close = close;
  }

  public Long getVolume() {
    return volume;
  }

  public void setVolume(Long volume) {
    this.volume = volume;
  }
}
