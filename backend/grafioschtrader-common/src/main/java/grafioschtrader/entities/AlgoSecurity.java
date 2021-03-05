package grafioschtrader.entities;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "algo_security")
@DiscriminatorValue("S")
public class AlgoSecurity extends AlgoAssetclassSecurity {

  private static final long serialVersionUID = 1L;

  @Column(name = "id_algo_assetclass_security_p")
  private Integer idAlgoSecurityParent;

  @JoinColumn(name = "id_securitycurrency", referencedColumnName = "id_securitycurrency")
  @ManyToOne
  private Security security;

  public Integer getIdAlgoSecurityParent() {
    return idAlgoSecurityParent;
  }

  public void setIdAlgoSecurityParent(Integer idAlgoSecurityParent) {
    this.idAlgoSecurityParent = idAlgoSecurityParent;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

}
