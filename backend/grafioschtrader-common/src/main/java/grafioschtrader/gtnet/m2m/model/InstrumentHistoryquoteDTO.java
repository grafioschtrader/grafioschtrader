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

  public List<HistoryquoteRecordDTO> getRecords() {
    return records;
  }

  public void setRecords(List<HistoryquoteRecordDTO> records) {
    this.records = records;
  }
}
