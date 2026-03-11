package grafioschtrader.tax.swiss.ech0196.model;

import java.time.LocalDate;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Tax value (Steuerwert) of a security position at the reference date.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196TaxValue {

  @XmlAttribute(required = true)
  @XmlJavaTypeAdapter(LocalDateAdapter.class)
  private LocalDate referenceDate;

  @XmlAttribute(required = true)
  private String quotationType;

  @XmlAttribute(required = true)
  private Double quantity;

  @XmlAttribute(required = true)
  private String balanceCurrency;

  @XmlAttribute
  private Double unitPrice;

  @XmlAttribute
  private Double balance;

  @XmlAttribute
  private Double exchangeRate;

  @XmlAttribute
  private Double value;

  @XmlAttribute
  private Boolean kursliste;

  public LocalDate getReferenceDate() { return referenceDate; }
  public void setReferenceDate(LocalDate referenceDate) { this.referenceDate = referenceDate; }

  public String getQuotationType() { return quotationType; }
  public void setQuotationType(String quotationType) { this.quotationType = quotationType; }

  public Double getQuantity() { return quantity; }
  public void setQuantity(Double quantity) { this.quantity = quantity; }

  public String getBalanceCurrency() { return balanceCurrency; }
  public void setBalanceCurrency(String balanceCurrency) { this.balanceCurrency = balanceCurrency; }

  public Double getUnitPrice() { return unitPrice; }
  public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

  public Double getBalance() { return balance; }
  public void setBalance(Double balance) { this.balance = balance; }

  public Double getExchangeRate() { return exchangeRate; }
  public void setExchangeRate(Double exchangeRate) { this.exchangeRate = exchangeRate; }

  public Double getValue() { return value; }
  public void setValue(Double value) { this.value = value; }

  public Boolean getKursliste() { return kursliste; }
  public void setKursliste(Boolean kursliste) { this.kursliste = kursliste; }
}
