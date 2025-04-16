package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.Auditable;
import grafiosch.entities.MultilanguageString;
import grafiosch.types.Language;
import grafioschtrader.types.TradingPlatformFeePlan;
import grafioschtrader.validation.ValidMultilanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Schema(description = """
It contains the information for the trading platform. It is the link between the template group and the depot. 
This makes it possible to import transactions from external files.""")
@Entity
@Table(name = TradingPlatformPlan.TABNAME)
public class TradingPlatformPlan extends Auditable implements Serializable {

  public static final String TABNAME = "trading_platform_plan";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_trading_platform_plan")
  private Integer idTradingPlatformPlan;

  @Schema(description = "Name of the trading platform Plan, this is multilingual.")
  @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
  @JoinColumn(name = "platform_plan_name_nls")
  @PropertyAlwaysUpdatable
  @ValidMultilanguage
  private MultilanguageString platformPlanNameNLS;

  @Schema(description = "The trading platforms have different bases for calculating transaction costs.")
  @Column(name = "transaction_fee_plan")
  @PropertyAlwaysUpdatable
  private byte transactionFeePlan;

  @Schema(description = """
      Assignment of a type of calculation of future transaction costs based on past transactions.
      This would make it easier to calculate hypothetical transaction costs.""")
  @JoinColumn(name = "id_trans_imp_platform", referencedColumnName = "id_trans_imp_platform")
  @ManyToOne
  @PropertyAlwaysUpdatable
  private ImportTransactionPlatform importTransactionPlatform;

  public Integer getIdTradingPlatformPlan() {
    return idTradingPlatformPlan;
  }

  public void setIdTradingPlatformPlan(Integer idTradingPlatformPlan) {
    this.idTradingPlatformPlan = idTradingPlatformPlan;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return getIdTradingPlatformPlan();
  }

  public MultilanguageString getPlatformPlanNameNLS() {
    return platformPlanNameNLS != null ? platformPlanNameNLS : (platformPlanNameNLS = new MultilanguageString());
  }

  public String getPlatformPlanNameByLanguage(final Language language) {
    return getPlatformPlanNameNLS().getText(language.getKey());
  }

  public void setPlatformPlanNameByLanguage(final String platformPlanName, final Language language) {
    getPlatformPlanNameNLS().addText(language.getKey(), platformPlanName);
  }

  public TradingPlatformFeePlan getTransactionFeePlan() {
    return TradingPlatformFeePlan.getTradingPlatformFeePlan(this.transactionFeePlan);
  }

  public void setTransactionFeePlan(TradingPlatformFeePlan transactionFeePlan) {
    this.transactionFeePlan = transactionFeePlan.getValue();
  }

  public ImportTransactionPlatform getImportTransactionPlatform() {
    return importTransactionPlatform;
  }

  public void setImportTransactionPlatform(ImportTransactionPlatform importTransactionPlatform) {
    this.importTransactionPlatform = importTransactionPlatform;
  }

  public void setPlatformPlanNameNLS(MultilanguageString platformPlanNameNLS) {
    this.platformPlanNameNLS = platformPlanNameNLS;
  }

}
