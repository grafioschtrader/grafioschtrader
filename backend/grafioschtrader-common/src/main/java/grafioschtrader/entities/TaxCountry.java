package grafioschtrader.entities;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = TaxCountry.TABNAME)
public class TaxCountry {

  public static final String TABNAME = "tax_country";

  @jakarta.persistence.Column(name = "tax_model_yaml", columnDefinition = "MEDIUMTEXT")
  @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
  private String taxModelYaml;

  public String getTaxModelYaml() {
    return taxModelYaml;
  }

  public void setTaxModelYaml(String taxModelYaml) {
    this.taxModelYaml = taxModelYaml;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_tax_country")
  private Integer idTaxCountry;

  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  @OneToMany(mappedBy = "taxCountry")
  @OrderBy("taxYear DESC")
  private List<TaxYear> taxYears;

  public TaxCountry() {
  }

  @jakarta.persistence.Transient
  public boolean isIctaxSupported() {
    return "CH".equals(countryCode);
  }

  @jakarta.persistence.Transient
  public boolean isHasTaxModel() {
    return taxModelYaml != null && !taxModelYaml.isBlank();
  }

  public Integer getIdTaxCountry() {
    return idTaxCountry;
  }

  public void setIdTaxCountry(Integer idTaxCountry) {
    this.idTaxCountry = idTaxCountry;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public List<TaxYear> getTaxYears() {
    return taxYears;
  }

  public void setTaxYears(List<TaxYear> taxYears) {
    this.taxYears = taxYears;
  }
}
