package grafioschtrader.connector.instrument.generic;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

/**
 * Request DTO for testing a generic connector endpoint. Contains the connector ID, endpoint type selector,
 * ticker/currency inputs, and optional date range for historical tests. Operates on saved (persisted) connector
 * definitions loaded from the database.
 */
public class GenericConnectorTestRequest {

  private Integer idGenericConnector;
  private String feedSupport;
  private String instrumentType;
  private String ticker;
  private String fromCurrency;
  private String toCurrency;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate fromDate;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate toDate;

  public Integer getIdGenericConnector() {
    return idGenericConnector;
  }

  public void setIdGenericConnector(Integer idGenericConnector) {
    this.idGenericConnector = idGenericConnector;
  }

  public String getFeedSupport() {
    return feedSupport;
  }

  public void setFeedSupport(String feedSupport) {
    this.feedSupport = feedSupport;
  }

  public String getInstrumentType() {
    return instrumentType;
  }

  public void setInstrumentType(String instrumentType) {
    this.instrumentType = instrumentType;
  }

  public String getTicker() {
    return ticker;
  }

  public void setTicker(String ticker) {
    this.ticker = ticker;
  }

  public String getFromCurrency() {
    return fromCurrency;
  }

  public void setFromCurrency(String fromCurrency) {
    this.fromCurrency = fromCurrency;
  }

  public String getToCurrency() {
    return toCurrency;
  }

  public void setToCurrency(String toCurrency) {
    this.toCurrency = toCurrency;
  }

  public LocalDate getFromDate() {
    return fromDate;
  }

  public void setFromDate(LocalDate fromDate) {
    this.fromDate = fromDate;
  }

  public LocalDate getToDate() {
    return toDate;
  }

  public void setToDate(LocalDate toDate) {
    this.toDate = toDate;
  }
}
