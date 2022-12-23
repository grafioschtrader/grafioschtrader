package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.types.Language;
import grafioschtrader.types.TradingPlatformFeePlan;
import grafioschtrader.validation.ValidMultilanguage;
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

/**
 * It contains the information for the trading platform.
 *
 *
 * @author Hugo Graf
 *
 */
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

  @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
  @JoinColumn(name = "platform_plan_name_nls")
  @PropertyAlwaysUpdatable
  @ValidMultilanguage
  private MultilanguageString platformPlanNameNLS;

  @Column(name = "transaction_fee_plan")
  @PropertyAlwaysUpdatable
  private byte transactionFeePlan;

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
