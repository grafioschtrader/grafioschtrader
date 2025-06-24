package grafiosch.integration.entities;

import grafiosch.entities.TenantBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = TenantBase.TABNAME)
public class Tenant extends TenantBase {
 
  private static final long serialVersionUID = 1L;

  public Tenant() {
  }
}
