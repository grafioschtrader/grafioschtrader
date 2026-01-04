package grafioschtrader.gtnet.m2m.model;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for a single historical price record in GTNet M2M communication.
 * Represents one day's OHLCV data for an instrument.
 */
@Schema(description = """
    Historical (EOD) price data for a single trading day. Contains OHLCV values with a date.
    Used within HistoryquoteExchangeMsg to transfer historical price series.""")
public class HistoryquoteRecordDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "Trading date for this historical quote")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private Date date;

  @Schema(description = "Opening price for this trading day")
  private Double open;

  @Schema(description = "Highest price for this trading day")
  private Double high;

  @Schema(description = "Lowest price for this trading day")
  private Double low;

  @Schema(description = "Closing price for this trading day (required)")
  private Double close;

  @Schema(description = "Traded volume for this trading day")
  private Long volume;

  public HistoryquoteRecordDTO() {
  }

  public HistoryquoteRecordDTO(Date date, Double open, Double high, Double low, Double close, Long volume) {
    this.date = date;
    this.open = open;
    this.high = high;
    this.low = low;
    this.close = close;
    this.volume = volume;
  }

  // Getters and setters

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
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
