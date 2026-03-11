package grafioschtrader.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Embeddable composite primary key for {@link TaxSecurityYearConfig}.
 */
@Embeddable
public class TaxSecurityYearConfigId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "id_tenant")
  private int idTenant;

  @Column(name = "tax_year")
  private short taxYear;

  @Column(name = "id_securitycurrency")
  private int idSecuritycurrency;

  public TaxSecurityYearConfigId() {
  }

  public TaxSecurityYearConfigId(int idTenant, short taxYear, int idSecuritycurrency) {
    this.idTenant = idTenant;
    this.taxYear = taxYear;
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public int getIdTenant() {
    return idTenant;
  }

  public short getTaxYear() {
    return taxYear;
  }

  public int getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TaxSecurityYearConfigId that = (TaxSecurityYearConfigId) o;
    return idTenant == that.idTenant && taxYear == that.taxYear && idSecuritycurrency == that.idSecuritycurrency;
  }

  @Override
  public int hashCode() {
    return Objects.hash(idTenant, taxYear, idSecuritycurrency);
  }
}
