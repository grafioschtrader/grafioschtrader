package grafioschtrader.tax.swiss.ech0196.model;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Securities section (Wertschriftenverzeichnis) of the eCH-0196 tax statement.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196ListOfSecurities {

  @XmlElement(name = "depot")
  private List<Ech0196Depot> depots;

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
  private Double totalLumpSumTaxCredit;

  @XmlAttribute(required = true)
  private Double totalNonRecoverableTax;

  @XmlAttribute(required = true)
  private Double totalAdditionalWithHoldingTaxUSA;

  @XmlAttribute(required = true)
  private Double totalGrossRevenueIUP;

  @XmlAttribute(required = true)
  private Double totalGrossRevenueConversion;

  public List<Ech0196Depot> getDepots() { return depots; }
  public void setDepots(List<Ech0196Depot> depots) { this.depots = depots; }

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

  public Double getTotalLumpSumTaxCredit() { return totalLumpSumTaxCredit; }
  public void setTotalLumpSumTaxCredit(Double totalLumpSumTaxCredit) { this.totalLumpSumTaxCredit = totalLumpSumTaxCredit; }

  public Double getTotalNonRecoverableTax() { return totalNonRecoverableTax; }
  public void setTotalNonRecoverableTax(Double totalNonRecoverableTax) { this.totalNonRecoverableTax = totalNonRecoverableTax; }

  public Double getTotalAdditionalWithHoldingTaxUSA() { return totalAdditionalWithHoldingTaxUSA; }
  public void setTotalAdditionalWithHoldingTaxUSA(Double v) { this.totalAdditionalWithHoldingTaxUSA = v; }

  public Double getTotalGrossRevenueIUP() { return totalGrossRevenueIUP; }
  public void setTotalGrossRevenueIUP(Double totalGrossRevenueIUP) { this.totalGrossRevenueIUP = totalGrossRevenueIUP; }

  public Double getTotalGrossRevenueConversion() { return totalGrossRevenueConversion; }
  public void setTotalGrossRevenueConversion(Double totalGrossRevenueConversion) { this.totalGrossRevenueConversion = totalGrossRevenueConversion; }
}
