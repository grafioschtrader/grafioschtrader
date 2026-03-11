package grafioschtrader.tax.swiss.ech0196.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Client (customer) information for the eCH-0196 tax statement.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Ech0196Client {

  @XmlAttribute(required = true)
  private String clientNumber;

  @XmlAttribute
  private String tin;

  @XmlAttribute
  private String salutation;

  @XmlAttribute
  private String firstName;

  @XmlAttribute
  private String lastName;

  public String getClientNumber() { return clientNumber; }
  public void setClientNumber(String clientNumber) { this.clientNumber = clientNumber; }

  public String getTin() { return tin; }
  public void setTin(String tin) { this.tin = tin; }

  public String getSalutation() { return salutation; }
  public void setSalutation(String salutation) { this.salutation = salutation; }

  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }

  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
}
