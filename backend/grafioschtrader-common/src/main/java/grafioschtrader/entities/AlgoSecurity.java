package grafioschtrader.entities;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = AlgoSecurity.TABNAME)
@DiscriminatorValue("S")
public class AlgoSecurity extends AlgoAssetclassSecurity {

  public static final String TABNAME = "algo_security";

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
