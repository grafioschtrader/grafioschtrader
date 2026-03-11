package grafioschtrader.entities;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Extracted per-security tax data from an ICTax Kursliste XML file. Contains the official Swiss tax value per unit and
 * links to associated dividend payment entries.
 */
@Entity
@Table(name = IctaxSecurityTaxData.TABNAME)
public class IctaxSecurityTaxData {

  public static final String TABNAME = "ictax_security_tax_data";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_ictax_data")
  private Integer idIctaxData;

  @Column(name = "id_tax_upload", nullable = false)
  private Integer idTaxUpload;

  @Column(name = "isin", nullable = false, length = 12)
  private String isin;

  @Column(name = "valor_number")
  private Integer valorNumber;

  @Column(name = "tax_value_chf")
  private Double taxValueChf;

  @Column(name = "quotation_type", length = 10)
  private String quotationType;

  @Column(name = "security_group", length = 20)
  private String securityGroup;

  @Column(name = "institution_name", length = 200)
  private String institutionName;

  @Column(name = "country", length = 5)
  private String country;

  @Column(name = "currency", length = 3)
  private String currency;

  @OneToMany(mappedBy = "ictaxSecurityTaxData", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<IctaxPayment> payments;

  public IctaxSecurityTaxData() {
  }

  public Integer getIdIctaxData() {
    return idIctaxData;
  }

  public void setIdIctaxData(Integer idIctaxData) {
    this.idIctaxData = idIctaxData;
  }

  public Integer getIdTaxUpload() {
    return idTaxUpload;
  }

  public void setIdTaxUpload(Integer idTaxUpload) {
    this.idTaxUpload = idTaxUpload;
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public Integer getValorNumber() {
    return valorNumber;
  }

  public void setValorNumber(Integer valorNumber) {
    this.valorNumber = valorNumber;
  }

  public Double getTaxValueChf() {
    return taxValueChf;
  }

  public void setTaxValueChf(Double taxValueChf) {
    this.taxValueChf = taxValueChf;
  }

  public String getQuotationType() {
    return quotationType;
  }

  public void setQuotationType(String quotationType) {
    this.quotationType = quotationType;
  }

  public String getSecurityGroup() {
    return securityGroup;
  }

  public void setSecurityGroup(String securityGroup) {
    this.securityGroup = securityGroup;
  }

  public String getInstitutionName() {
    return institutionName;
  }

  public void setInstitutionName(String institutionName) {
    this.institutionName = institutionName;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public List<IctaxPayment> getPayments() {
    return payments;
  }

  public void setPayments(List<IctaxPayment> payments) {
    this.payments = payments;
  }
}
