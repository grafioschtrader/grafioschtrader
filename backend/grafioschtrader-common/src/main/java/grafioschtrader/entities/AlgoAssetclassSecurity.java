package grafioschtrader.entities;

import java.io.Serializable;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Base class for a strategy on asset class or security.
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = AlgoAssetclassSecurity.TABNAME)
public abstract class AlgoAssetclassSecurity extends AlgoTopAssetSecurity implements Serializable {

  public static final String TABNAME = "algo_assetclass_security";

  private static final long serialVersionUID = 1L;

  /**
   * In a case of simulation this security account is used at first priority.
   */
  @Column(name = "id_securitycash_account_1")
  @PropertyAlwaysUpdatable
  protected Integer idSecurityaccount1;

  @Column(name = "id_securitycash_account_2")
  @PropertyAlwaysUpdatable
  protected Integer idSecurityaccount2;

  public Integer getIdSecurityaccount1() {
    return idSecurityaccount1;
  }

  public void setIdSecurityaccount1(Integer idSecurityaccount1) {
    this.idSecurityaccount1 = idSecurityaccount1;
  }

  public Integer getIdSecurityaccount2() {
    return idSecurityaccount2;
  }

  public void setIdSecurityaccount2(Integer idSecurityaccount2) {
    this.idSecurityaccount2 = idSecurityaccount2;
  }

}
