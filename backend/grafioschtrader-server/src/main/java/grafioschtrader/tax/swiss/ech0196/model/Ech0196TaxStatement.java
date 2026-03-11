package grafioschtrader.tax.swiss.ech0196.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Root element of the eCH-0196 v2.2.0 tax statement (eSteuerauszug).
 */
@XmlRootElement(name = "taxStatement")
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196TaxStatement {

  @XmlElement(required = true)
  private Ech0196Institution institution;

  @XmlElement(name = "client", required = true)
  private List<Ech0196Client> clients;

  @XmlElement
  private Ech0196ListOfSecurities listOfSecurities;

  @XmlAttribute(required = true)
  private String id;

  @XmlAttribute(required = true)
  @XmlJavaTypeAdapter(LocalDateTimeAdapter.class)
  private LocalDateTime creationDate;

  @XmlAttribute(required = true)
  @XmlJavaTypeAdapter(GYearAdapter.class)
  @XmlSchemaType(name = "gYear")
  private Integer taxPeriod;

  @XmlAttribute(required = true)
  @XmlJavaTypeAdapter(LocalDateAdapter.class)
  private LocalDate periodFrom;

  @XmlAttribute(required = true)
  @XmlJavaTypeAdapter(LocalDateAdapter.class)
  private LocalDate periodTo;

  @XmlAttribute
  private String country;

  @XmlAttribute(required = true)
  private String canton;

  @XmlAttribute(required = true)
  private Double totalTaxValue;

  @XmlAttribute(required = true)
  private Double totalGrossRevenueA;

  @XmlAttribute
  private Double totalGrossRevenueACanton;

  @XmlAttribute(required = true)
  private Double totalGrossRevenueB;

  @XmlAttribute
  private Double totalGrossRevenueBCanton;

  @XmlAttribute(required = true)
  private Double totalWithHoldingTaxClaim;

  @XmlAttribute(required = true)
  private Integer minorVersion;

  // Getters and setters

  public Ech0196Institution getInstitution() { return institution; }
  public void setInstitution(Ech0196Institution institution) { this.institution = institution; }

  public List<Ech0196Client> getClients() { return clients; }
  public void setClients(List<Ech0196Client> clients) { this.clients = clients; }

  public Ech0196ListOfSecurities getListOfSecurities() { return listOfSecurities; }
  public void setListOfSecurities(Ech0196ListOfSecurities listOfSecurities) { this.listOfSecurities = listOfSecurities; }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }

  public LocalDateTime getCreationDate() { return creationDate; }
  public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }

  public Integer getTaxPeriod() { return taxPeriod; }
  public void setTaxPeriod(Integer taxPeriod) { this.taxPeriod = taxPeriod; }

  public LocalDate getPeriodFrom() { return periodFrom; }
  public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }

  public LocalDate getPeriodTo() { return periodTo; }
  public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }

  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }

  public String getCanton() { return canton; }
  public void setCanton(String canton) { this.canton = canton; }

  public Double getTotalTaxValue() { return totalTaxValue; }
  public void setTotalTaxValue(Double totalTaxValue) { this.totalTaxValue = totalTaxValue; }

  public Double getTotalGrossRevenueA() { return totalGrossRevenueA; }
  public void setTotalGrossRevenueA(Double totalGrossRevenueA) { this.totalGrossRevenueA = totalGrossRevenueA; }

  public Double getTotalGrossRevenueACanton() { return totalGrossRevenueACanton; }
  public void setTotalGrossRevenueACanton(Double totalGrossRevenueACanton) { this.totalGrossRevenueACanton = totalGrossRevenueACanton; }

  public Double getTotalGrossRevenueB() { return totalGrossRevenueB; }
  public void setTotalGrossRevenueB(Double totalGrossRevenueB) { this.totalGrossRevenueB = totalGrossRevenueB; }

  public Double getTotalGrossRevenueBCanton() { return totalGrossRevenueBCanton; }
  public void setTotalGrossRevenueBCanton(Double totalGrossRevenueBCanton) { this.totalGrossRevenueBCanton = totalGrossRevenueBCanton; }

  public Double getTotalWithHoldingTaxClaim() { return totalWithHoldingTaxClaim; }
  public void setTotalWithHoldingTaxClaim(Double totalWithHoldingTaxClaim) { this.totalWithHoldingTaxClaim = totalWithHoldingTaxClaim; }

  public Integer getMinorVersion() { return minorVersion; }
  public void setMinorVersion(Integer minorVersion) { this.minorVersion = minorVersion; }
}
