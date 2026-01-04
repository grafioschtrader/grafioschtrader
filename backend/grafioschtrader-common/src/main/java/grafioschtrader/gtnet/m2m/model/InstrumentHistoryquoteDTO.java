package grafioschtrader.gtnet.m2m.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for historical price data of a single instrument in GTNet M2M communication.
 * Contains instrument identification plus a list of historical price records.
 *
 * Instrument identification:
 * - Security: isin + currency (toCurrency is null)
 * - Currency pair: currency (fromCurrency) + toCurrency (isin is null)
 */
@Schema(description = """
    Historical price data for a single instrument. In requests, contains date range to query.
    In responses, contains the actual historical price records. For securities, identification is via ISIN + currency.
    For currency pairs, identification is via currency (as fromCurrency) + toCurrency.""")
public class InstrumentHistoryquoteDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "ISIN for securities, null for currency pairs")
  private String isin;

  @Schema(description = "Currency code for securities, or fromCurrency for currency pairs")
  private String currency;

  @Schema(description = "toCurrency for currency pairs, null for securities")
  private String toCurrency;

  @Schema(description = "Start date of the requested/provided date range (inclusive)")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private Date fromDate;

  @Schema(description = "End date of the requested/provided date range (inclusive)")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private Date toDate;

  @Schema(description = """
      When true in a response, indicates the supplier is interested in receiving historical data for this instrument
      but cannot provide it. The consumer should push data back in a subsequent message if available.""")
  private Boolean wantsToReceive;

  @Schema(description = """
      The date from which the supplier wants to receive historical data (typically the day after their most recent
      data). Only populated when wantsToReceive is true.""")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private Date wantsDataFromDate;

  @Schema(description = "List of historical price records within the date range")
  private List<HistoryquoteRecordDTO> records = new ArrayList<>();

  public InstrumentHistoryquoteDTO() {
  }

  /**
   * Creates a request DTO for a security (identified by ISIN + currency).
   */
  public static InstrumentHistoryquoteDTO forSecurityRequest(String isin, String currency, Date fromDate, Date toDate) {
    InstrumentHistoryquoteDTO dto = new InstrumentHistoryquoteDTO();
    dto.isin = isin;
    dto.currency = currency;
    dto.toCurrency = null;
    dto.fromDate = fromDate;
    dto.toDate = toDate;
    return dto;
  }

  /**
   * Creates a request DTO for a currency pair (identified by fromCurrency + toCurrency).
   */
  public static InstrumentHistoryquoteDTO forCurrencypairRequest(String fromCurrency, String toCurrency,
      Date fromDate, Date toDate) {
    InstrumentHistoryquoteDTO dto = new InstrumentHistoryquoteDTO();
    dto.isin = null;
    dto.currency = fromCurrency;
    dto.toCurrency = toCurrency;
    dto.fromDate = fromDate;
    dto.toDate = toDate;
    return dto;
  }

  /**
   * Creates a "want to receive" response DTO for a security.
   * Used by AC_OPEN servers to indicate interest in receiving historical data they cannot provide.
   *
   * @param isin the ISIN of the security
   * @param currency the currency of the security
   * @param wantsFromDate the date from which data is wanted (typically most recent local data + 1 day)
   */
  public static InstrumentHistoryquoteDTO forSecurityWantToReceive(String isin, String currency, Date wantsFromDate) {
    InstrumentHistoryquoteDTO dto = new InstrumentHistoryquoteDTO();
    dto.isin = isin;
    dto.currency = currency;
    dto.wantsToReceive = true;
    dto.wantsDataFromDate = wantsFromDate;
    return dto;
  }

  /**
   * Creates a "want to receive" response DTO for a currency pair.
   * Used by AC_OPEN servers to indicate interest in receiving historical data they cannot provide.
   *
   * @param fromCurrency the source currency
   * @param toCurrency the target currency
   * @param wantsFromDate the date from which data is wanted (typically most recent local data + 1 day)
   */
  public static InstrumentHistoryquoteDTO forCurrencypairWantToReceive(String fromCurrency, String toCurrency,
      Date wantsFromDate) {
    InstrumentHistoryquoteDTO dto = new InstrumentHistoryquoteDTO();
    dto.currency = fromCurrency;
    dto.toCurrency = toCurrency;
    dto.wantsToReceive = true;
    dto.wantsDataFromDate = wantsFromDate;
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

  /**
   * Returns the number of records in this DTO.
   */
  @JsonIgnore
  public int getRecordCount() {
    return records != null ? records.size() : 0;
  }

  /**
   * Checks if this DTO is a "want to receive" response (supplier wants data but cannot provide it).
   */
  @JsonIgnore
  public boolean isWantToReceiveResponse() {
    return Boolean.TRUE.equals(wantsToReceive);
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

  public Date getFromDate() {
    return fromDate;
  }

  public void setFromDate(Date fromDate) {
    this.fromDate = fromDate;
  }

  public Date getToDate() {
    return toDate;
  }

  public void setToDate(Date toDate) {
    this.toDate = toDate;
  }

  public Boolean getWantsToReceive() {
    return wantsToReceive;
  }

  public void setWantsToReceive(Boolean wantsToReceive) {
    this.wantsToReceive = wantsToReceive;
  }

  public Date getWantsDataFromDate() {
    return wantsDataFromDate;
  }

  public void setWantsDataFromDate(Date wantsDataFromDate) {
    this.wantsDataFromDate = wantsDataFromDate;
  }

  public List<HistoryquoteRecordDTO> getRecords() {
    return records;
  }

  public void setRecords(List<HistoryquoteRecordDTO> records) {
    this.records = records;
  }
}
