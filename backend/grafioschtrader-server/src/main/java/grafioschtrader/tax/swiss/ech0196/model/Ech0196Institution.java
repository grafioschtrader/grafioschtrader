package grafioschtrader.tax.swiss.ech0196.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Financial institution information for the eCH-0196 tax statement.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196Institution {

  @XmlAttribute
  private String lei;

  @XmlAttribute(required = true)
  private String name;

  public String getLei() { return lei; }
  public void setLei(String lei) { this.lei = lei; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
}
