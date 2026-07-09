package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.entities.TenantBaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = """
    Manual per-tenant override of the ICTax-computed taxable income (ictaxTotalPaymentValueChf) for one security
    and one tax year. Either the position's computed taxableAmountMC is used instead (useTaxableAmount) or a
    directly entered value (taxableIncome) replaces the ICTax figure; both override fields are mutually exclusive.
    A record may also be comment-only (note set, no override) to document a tax position.""")
@Entity
@Table(name = TaxYearCorrection.TABNAME)
public class TaxYearCorrection extends TenantBaseID implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final String TABNAME = "tax_year_correction";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_tax_year_correction")
  private Integer idTaxYearCorrection;

  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "Reference to the security this correction applies to")
  @NotNull
  @PropertySelectiveUpdatableOrWhenNull
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Schema(description = "The tax year this correction applies to. Unique together with tenant and security.")
  @NotNull
  @PropertySelectiveUpdatableOrWhenNull
  @Column(name = "tax_year")
  private Short taxYear;

  @Schema(description = """
      When true, the position's computed taxableAmountMC replaces the ICTax payment total in the dividends report
      and the tax statement. Mutually exclusive with taxableIncome.""")
  @PropertyAlwaysUpdatable
  @Column(name = "use_taxable_amount")
  private boolean useTaxableAmount;

  @Schema(description = """
      Directly entered taxable income that replaces the ICTax payment total. Mutually exclusive with
      useTaxableAmount. Null when not used.""")
  @PropertyAlwaysUpdatable
  @Column(name = "taxable_income")
  private Double taxableIncome;

  @Schema(description = "User comment on this correction, e.g. why the ICTax value is wrong")
  @Size(max = 1024)
  @PropertyAlwaysUpdatable
  @Column(name = "note")
  private String note;

  public TaxYearCorrection() {
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idTaxYearCorrection;
  }

  public Integer getIdTaxYearCorrection() {
    return idTaxYearCorrection;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public Short getTaxYear() {
    return taxYear;
  }

  public void setTaxYear(Short taxYear) {
    this.taxYear = taxYear;
  }

  public boolean isUseTaxableAmount() {
    return useTaxableAmount;
  }

  public void setUseTaxableAmount(boolean useTaxableAmount) {
    this.useTaxableAmount = useTaxableAmount;
  }

  public Double getTaxableIncome() {
    return taxableIncome;
  }

  public void setTaxableIncome(Double taxableIncome) {
    this.taxableIncome = taxableIncome;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  @Override
  public String toString() {
    return "TaxYearCorrection [idTaxYearCorrection=" + idTaxYearCorrection + ", idTenant=" + idTenant
        + ", idSecuritycurrency=" + idSecuritycurrency + ", taxYear=" + taxYear + ", useTaxableAmount="
        + useTaxableAmount + ", taxableIncome=" + taxableIncome + "]";
  }
}
