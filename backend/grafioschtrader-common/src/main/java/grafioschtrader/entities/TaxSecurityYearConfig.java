package grafioschtrader.entities;

import java.io.Serializable;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Marks a security as excluded from the eCH-0196 tax statement export for a specific tenant and year. The presence of a
 * row means the security is excluded; absence means it is included.
 */
@Entity
@Table(name = "tax_security_year_config")
public class TaxSecurityYearConfig implements Serializable {

  private static final long serialVersionUID = 1L;

  @EmbeddedId
  private TaxSecurityYearConfigId id;

  public TaxSecurityYearConfig() {
  }

  public TaxSecurityYearConfig(int idTenant, short taxYear, int idSecuritycurrency) {
    this.id = new TaxSecurityYearConfigId(idTenant, taxYear, idSecuritycurrency);
  }

  public TaxSecurityYearConfigId getId() {
    return id;
  }

  public void setId(TaxSecurityYearConfigId id) {
    this.id = id;
  }

  public int getIdSecuritycurrency() {
    return id.getIdSecuritycurrency();
  }
}
