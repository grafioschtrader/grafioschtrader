package grafioschtrader.tax.swiss.ech0196.model;

import java.time.LocalDate;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Stock mutation (Bestandesmutation) for a security in the eCH-0196 tax statement.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196Stock {

  @XmlAttribute(required = true)
  @XmlJavaTypeAdapter(LocalDateAdapter.class)
  private LocalDate referenceDate;

  @XmlAttribute(required = true)
  private boolean mutation;

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
  private String name;

  @XmlAttribute
  private Double reductionCost;

  public LocalDate getReferenceDate() { return referenceDate; }
  public void setReferenceDate(LocalDate referenceDate) { this.referenceDate = referenceDate; }

  public boolean isMutation() { return mutation; }
  public void setMutation(boolean mutation) { this.mutation = mutation; }

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

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public Double getReductionCost() { return reductionCost; }
  public void setReductionCost(Double reductionCost) { this.reductionCost = reductionCost; }
}
