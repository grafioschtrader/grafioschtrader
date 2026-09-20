package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/** Explicit simulation inputs, with all monetary amounts expressed in instrument currency. */
@Schema(description = "Tax preview inputs; omitted optional metadata means unknown.")
public class TaxEstimateRequest {
  public enum EventKind {
    BUY, SELL, DIVIDEND, SECURITY_INTEREST
  }

  public EventKind eventKind;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate eventDate;
  public String currency;
  public double units;
  public double price;
  public double cleanValue;
  public double accruedInterest;
  public double grossIncome;
  public String instrument;
  public String assetclass;
  public String mic;
  public String issuerCountry;
  public String dealerCountry;
  public String exchangeCountry;
  public Boolean exemptInvestor;
  public String countryCode;
  public String yaml;
}
