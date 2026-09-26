package grafioschtrader.entities;

import java.io.Serializable;

import grafiosch.common.PropertyAlwaysUpdatable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Base class for a strategy on asset class or security.
 */
@Entity
@Table(name = AlgoAssetclassSecurity.TABNAME)
public abstract class AlgoAssetclassSecurity extends AlgoTopAssetSecurity implements Serializable {

  public static final String TABNAME = "algo_assetclass_security";

  private static final long serialVersionUID = 1L;

  /**
   * Security account a simulation trades the instruments of this node at, because the user found it cheapest there. A
   * purchase is booked on it even when it holds too little cash; the missing money is moved to it from the other
   * accounts of the environment. An instrument node without a priority inherits the one of its asset class. An
   * instrument the environment already holds stays on the account it is held in. Its trading periods must allow the
   * instrument type.
   */
  @Column(name = "id_securitycash_account_1")
  @PropertyAlwaysUpdatable
  protected Integer idSecurityaccount1;

  /**
   * Security account used instead of the first one when the trading periods of the first do not allow the instrument on
   * the day of a simulated fill. Requires the first one and must differ from it.
   */
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
