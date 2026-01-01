package grafioschtrader.gtnet.m2m.model;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for intraday price data of a single instrument in GTNet M2M communication.
 * Used in both request (with current timestamps) and response (with updated prices).
 *
 * Instrument identification:
 * - Security: isin + currency (toCurrency is null)
 * - Currency pair: currency (fromCurrency) + toCurrency (isin is null)
 */
@Schema(description = """
    Intraday price data for a single instrument. For securities, identification is via ISIN + currency.
    For currency pairs, identification is via currency (as fromCurrency) + toCurrency.""")
public class InstrumentPriceDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "ISIN for securities, null for currency pairs")
  private String isin;

  @Schema(description = "Currency code for securities, or fromCurrency for currency pairs")
  private String currency;

  @Schema(description = "toCurrency for currency pairs, null for securities")
  private String toCurrency;

  @Schema(description = "Timestamp of the last price update. Null in request means no local data available")
  private Date timestamp;

  @Schema(description = "Opening price for the trading day")
  private Double open;

  @Schema(description = "Highest price for the trading day")
  private Double high;

  @Schema(description = "Lowest price for the trading day")
  private Double low;

  @Schema(description = "Most recent price (last traded price)")
  private Double last;

  @Schema(description = "Trading volume for the day")
  private Long volume;

  public InstrumentPriceDTO() {
  }

  /**
   * Creates a price DTO from a Security entity.
   */
  public static InstrumentPriceDTO fromSecurity(Security security) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.isin = security.getIsin();
    dto.currency = security.getCurrency();
    dto.toCurrency = null;
    dto.timestamp = security.getSTimestamp();
    dto.open = security.getSOpen();
    dto.high = security.getSHigh();
    dto.low = security.getSLow();
    dto.last = security.getSLast();
    dto.volume = security.getSVolume();
    return dto;
  }

  /**
   * Creates a price DTO from a Currencypair entity.
   * Note: Currency pairs don't have volume data (only cryptocurrencies may have volume).
   */
  public static InstrumentPriceDTO fromCurrencypair(Currencypair currencypair) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.isin = null;
    dto.currency = currencypair.getFromCurrency();
    dto.toCurrency = currencypair.getToCurrency();
    dto.timestamp = currencypair.getSTimestamp();
    dto.open = currencypair.getSOpen();
    dto.high = currencypair.getSHigh();
    dto.low = currencypair.getSLow();
    dto.last = currencypair.getSLast();
    dto.volume = null; // Currencypair doesn't track volume
    return dto;
  }

  /**
   * Checks if this DTO represents a security (has ISIN).
   */
  @JsonIgnore
  public boolean isSecurity() {
    return isin != null;
  }

  /**
   * Checks if this DTO represents a currency pair (no ISIN, has toCurrency).
   */
  @JsonIgnore
  public boolean isCurrencypair() {
    return isin == null && toCurrency != null;
  }

  /**
   * Returns a unique key for this instrument (ISIN+currency for securities, from+to for pairs).
   */
  @JsonIgnore
  public String getKey() {
    if (isSecurity()) {
      return isin + ":" + currency;
    } else {
      return currency + ":" + toCurrency;
    }
  }

  // Getters and setters

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getToCurrency() {
    return toCurrency;
  }

  public void setToCurrency(String toCurrency) {
    this.toCurrency = toCurrency;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
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

  public Double getLast() {
    return last;
  }

  public void setLast(Double last) {
    this.last = last;
  }

  public Long getVolume() {
    return volume;
  }

  public void setVolume(Long volume) {
    this.volume = volume;
  }
}
