package grafioschtrader.tax.swiss.ech0196.model;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Securities depot within the eCH-0196 tax statement.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196Depot {

  @XmlElement(name = "security")
  private List<Ech0196Security> securities;

  @XmlAttribute(required = true)
  private String depotNumber;

  public List<Ech0196Security> getSecurities() { return securities; }
  public void setSecurities(List<Ech0196Security> securities) { this.securities = securities; }

  public String getDepotNumber() { return depotNumber; }
  public void setDepotNumber(String depotNumber) { this.depotNumber = depotNumber; }
}
