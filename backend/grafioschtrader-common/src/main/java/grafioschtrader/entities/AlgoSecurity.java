package grafioschtrader.entities;

import grafioschtrader.algo.strategy.model.StrategyHelper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = AlgoSecurity.TABNAME)
@DiscriminatorValue(StrategyHelper.SECURITY_LEVEL_LETTER)
@Schema(description = """
    This can be done irrespective of the affiliation to an asset class.
    This means that the alarm or strategy is applied independently.
    However, it can also refer to a specific securities account.
    If neither is defined, it is applied to the client.
    """)
public class AlgoSecurity extends AlgoAssetclassSecurity {

  public static final String TABNAME = "algo_security";

  private static final long serialVersionUID = 1L;

  @Schema(description = """
      Optional reference to an asset class. This is useful for a strategy if several securities belong to this asset class.""")
  @Column(name = "id_algo_security_parent")
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
