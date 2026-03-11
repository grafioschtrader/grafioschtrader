package grafioschtrader.tax.swiss.ech0196.model;

import java.time.LocalDate;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Payment/income entry (Ertrag) for a security in the eCH-0196 tax statement.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196Payment {

  @XmlAttribute(required = true)
  @XmlJavaTypeAdapter(LocalDateAdapter.class)
  private LocalDate paymentDate;

  @XmlAttribute
  @XmlJavaTypeAdapter(LocalDateAdapter.class)
  private LocalDate exDate;

  @XmlAttribute(required = true)
  private String quotationType;

  @XmlAttribute(required = true)
  private Double quantity;

  @XmlAttribute(required = true)
  private String amountCurrency;

  @XmlAttribute
  private Double amountPerUnit;

  @XmlAttribute
  private Double amount;

  @XmlAttribute
  private Double exchangeRate;

  @XmlAttribute
  private Double grossRevenueA;

  @XmlAttribute
  private Double grossRevenueACanton;

  @XmlAttribute
  private Double grossRevenueB;

  @XmlAttribute
  private Double grossRevenueBCanton;

  @XmlAttribute
  private Double withHoldingTaxClaim;

  @XmlAttribute
  private Boolean lumpSumTaxCredit;

  @XmlAttribute
  private Double lumpSumTaxCreditPercent;

  @XmlAttribute
  private Double lumpSumTaxCreditAmount;

  @XmlAttribute
  private Boolean kursliste;

  @XmlAttribute
  private String sign;

  // Getters and setters

  public LocalDate getPaymentDate() { return paymentDate; }
  public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

  public LocalDate getExDate() { return exDate; }
  public void setExDate(LocalDate exDate) { this.exDate = exDate; }

  public String getQuotationType() { return quotationType; }
  public void setQuotationType(String quotationType) { this.quotationType = quotationType; }

  public Double getQuantity() { return quantity; }
  public void setQuantity(Double quantity) { this.quantity = quantity; }

  public String getAmountCurrency() { return amountCurrency; }
  public void setAmountCurrency(String amountCurrency) { this.amountCurrency = amountCurrency; }

  public Double getAmountPerUnit() { return amountPerUnit; }
  public void setAmountPerUnit(Double amountPerUnit) { this.amountPerUnit = amountPerUnit; }

  public Double getAmount() { return amount; }
  public void setAmount(Double amount) { this.amount = amount; }

  public Double getExchangeRate() { return exchangeRate; }
  public void setExchangeRate(Double exchangeRate) { this.exchangeRate = exchangeRate; }

  public Double getGrossRevenueA() { return grossRevenueA; }
  public void setGrossRevenueA(Double grossRevenueA) { this.grossRevenueA = grossRevenueA; }

  public Double getGrossRevenueACanton() { return grossRevenueACanton; }
  public void setGrossRevenueACanton(Double grossRevenueACanton) { this.grossRevenueACanton = grossRevenueACanton; }

  public Double getGrossRevenueB() { return grossRevenueB; }
  public void setGrossRevenueB(Double grossRevenueB) { this.grossRevenueB = grossRevenueB; }

  public Double getGrossRevenueBCanton() { return grossRevenueBCanton; }
  public void setGrossRevenueBCanton(Double grossRevenueBCanton) { this.grossRevenueBCanton = grossRevenueBCanton; }

  public Double getWithHoldingTaxClaim() { return withHoldingTaxClaim; }
  public void setWithHoldingTaxClaim(Double withHoldingTaxClaim) { this.withHoldingTaxClaim = withHoldingTaxClaim; }

  public Boolean getLumpSumTaxCredit() { return lumpSumTaxCredit; }
  public void setLumpSumTaxCredit(Boolean lumpSumTaxCredit) { this.lumpSumTaxCredit = lumpSumTaxCredit; }

  public Double getLumpSumTaxCreditPercent() { return lumpSumTaxCreditPercent; }
  public void setLumpSumTaxCreditPercent(Double lumpSumTaxCreditPercent) { this.lumpSumTaxCreditPercent = lumpSumTaxCreditPercent; }

  public Double getLumpSumTaxCreditAmount() { return lumpSumTaxCreditAmount; }
  public void setLumpSumTaxCreditAmount(Double lumpSumTaxCreditAmount) { this.lumpSumTaxCreditAmount = lumpSumTaxCreditAmount; }

  public Boolean getKursliste() { return kursliste; }
  public void setKursliste(Boolean kursliste) { this.kursliste = kursliste; }

  public String getSign() { return sign; }
  public void setSign(String sign) { this.sign = sign; }
}
