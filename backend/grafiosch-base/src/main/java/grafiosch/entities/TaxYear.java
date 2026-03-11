package grafiosch.entities;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = TaxYear.TABNAME)
public class TaxYear {

  public static final String TABNAME = "tax_year";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_tax_year")
  private Integer idTaxYear;

  @JsonIgnore
  @ManyToOne
  @JoinColumn(name = "id_tax_country", nullable = false)
  private TaxCountry taxCountry;

  @Column(name = "id_tax_country", insertable = false, updatable = false)
  private Integer idTaxCountry;

  @Column(name = "tax_year", nullable = false)
  private Short taxYear;

  @OneToMany(mappedBy = "taxYear")
  @OrderBy("uploadDate DESC")
  private List<TaxUpload> taxUploads;

  public TaxYear() {
  }

  public Integer getIdTaxYear() {
    return idTaxYear;
  }

  public void setIdTaxYear(Integer idTaxYear) {
    this.idTaxYear = idTaxYear;
  }

  public TaxCountry getTaxCountry() {
    return taxCountry;
  }

  public void setTaxCountry(TaxCountry taxCountry) {
    this.taxCountry = taxCountry;
  }

  public Integer getIdTaxCountry() {
    return idTaxCountry;
  }

  public void setIdTaxCountry(Integer idTaxCountry) {
    this.idTaxCountry = idTaxCountry;
  }

  public Short getTaxYear() {
    return taxYear;
  }

  public void setTaxYear(Short taxYear) {
    this.taxYear = taxYear;
  }

  public List<TaxUpload> getTaxUploads() {
    return taxUploads;
  }

  public void setTaxUploads(List<TaxUpload> taxUploads) {
    this.taxUploads = taxUploads;
  }
}
