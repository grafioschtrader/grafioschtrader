package grafioschtrader.tax.swiss.ech0196.model;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Individual security position within an eCH-0196 depot.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196Security {

  @XmlElement
  private Ech0196TaxValue taxValue;

  @XmlElement(name = "payment")
  private List<Ech0196Payment> payments;

  @XmlElement(name = "stock")
  private List<Ech0196Stock> stocks;

  @XmlAttribute(required = true)
  private int positionId;

  @XmlAttribute
  private Integer valorNumber;

  @XmlAttribute
  private String isin;

  @XmlAttribute(required = true)
  private String country;

  @XmlAttribute
  private String city;

  @XmlAttribute(required = true)
  private String currency;

  @XmlAttribute(required = true)
  private String quotationType;

  @XmlAttribute
  private Double nominalValue;

  @XmlAttribute(required = true)
  private String securityCategory;

  @XmlAttribute
  private String securityType;

  @XmlAttribute(required = true)
  private String securityName;

  // Getters and setters

  public Ech0196TaxValue getTaxValue() { return taxValue; }
  public void setTaxValue(Ech0196TaxValue taxValue) { this.taxValue = taxValue; }

  public List<Ech0196Payment> getPayments() { return payments; }
  public void setPayments(List<Ech0196Payment> payments) { this.payments = payments; }

  public List<Ech0196Stock> getStocks() { return stocks; }
  public void setStocks(List<Ech0196Stock> stocks) { this.stocks = stocks; }

  public int getPositionId() { return positionId; }
  public void setPositionId(int positionId) { this.positionId = positionId; }

  public Integer getValorNumber() { return valorNumber; }
  public void setValorNumber(Integer valorNumber) { this.valorNumber = valorNumber; }

  public String getIsin() { return isin; }
  public void setIsin(String isin) { this.isin = isin; }

  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }

  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }

  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }

  public String getQuotationType() { return quotationType; }
  public void setQuotationType(String quotationType) { this.quotationType = quotationType; }

  public Double getNominalValue() { return nominalValue; }
  public void setNominalValue(Double nominalValue) { this.nominalValue = nominalValue; }

  public String getSecurityCategory() { return securityCategory; }
  public void setSecurityCategory(String securityCategory) { this.securityCategory = securityCategory; }

  public String getSecurityType() { return securityType; }
  public void setSecurityType(String securityType) { this.securityType = securityType; }

  public String getSecurityName() { return securityName; }
  public void setSecurityName(String securityName) { this.securityName = securityName; }
}
