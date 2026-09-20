package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One line of the rebalancing plan of an AlgoTop: what a node of the hierarchy is supposed to hold, what it actually
 * holds and what would close the difference. A recommendation is a proposal for the user, never a booking - the live
 * evaluation writes these rows and a notification, and leaves the portfolio untouched.
 *
 * <p>
 * The rows are the current plan rather than a history. Each evaluation of an AlgoTop replaces its own rows, which is
 * what the unique key over tenant, AlgoTop, level and node expresses. {@code runDate} therefore only answers whether
 * today's evaluation has already run; the start of the periodic interval is {@code checkpointDate}, which each plan
 * carries over from the one it replaces until the next checkpoint falls due. Keeping every past plan would need a run
 * entity of its own, which belongs to the historical replay rather than to a live recommendation.
 * </p>
 *
 * <p>
 * {@code idTenant} is the tenant the plan was calculated for, which is the tenant that holds the positions. The AlgoTop
 * itself always belongs to the main tenant even when a simulation environment is active, so the two are not the same
 * identity and the tenant is part of the key.
 * </p>
 *
 * <p>
 * The entity carries no limit key. It has no create or update endpoint, so neither the explicit MAX registration nor
 * the derived daily budget of an ordinary user-writable entity applies to it; its size is bounded from above by the
 * caps on the hierarchy it mirrors, and by the replacement of the previous plan on every run.
 * </p>
 */
@Entity
@Table(name = AlgoRecommendation.TABNAME)
public class AlgoRecommendation extends TenantBaseID implements Serializable {

  public static final String TABNAME = "algo_recommendation";

  private static final long serialVersionUID = 1L;

  /** Auto-generated primary key. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_algo_recommendation")
  private Integer idAlgoRecommendation;

  /** Tenant whose holdings were evaluated. */
  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  /** The AlgoTop whose plan this line belongs to. */
  @Basic(optional = false)
  @Column(name = "id_algo_assetclass_security")
  private Integer idAlgoTop;

  /** The rebalancing strategy on that AlgoTop which produced the line. */
  @Basic(optional = false)
  @Column(name = "id_algo_strategy")
  private Integer idAlgoStrategy;

  /**
   * Level of the hierarchy described by this line, using its discriminator letters: T for the AlgoTop, A for an asset
   * class or named bucket, and S for a single instrument.
   */
  @Basic(optional = false)
  @Column(name = "level_type")
  private String levelType;

  /** The AlgoTop, AlgoAssetclass or AlgoSecurity node this line describes. */
  @Basic(optional = false)
  @Column(name = "id_node")
  private Integer idNode;

  /** The instrument of a security-level line; null on the top and bucket levels. */
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  /** Day the plan was calculated; tells whether today's evaluation has already run. */
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @Column(name = "run_date")
  private LocalDate runDate;

  /**
   * Valuation day of the last periodic checkpoint of the AlgoTop, carried over unchanged by the daily plans in between.
   * The checkpoint interval counts from this day; null when no checkpoint has been recorded yet, which makes the next
   * evaluation one.
   */
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "checkpoint_date")
  private LocalDate checkpointDate;

  /** Closing day whose holdings and prices the plan was calculated from. */
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @Column(name = "valuation_date")
  private LocalDate valuationDate;

  /** Tenant currency in which every monetary amount of this line is expressed. */
  @Basic(optional = false)
  @Column(name = "currency")
  private String currency;

  /** Target share of total net equity in percentage points. */
  @Column(name = "target_percentage")
  private Double targetPercentage;

  /** Actual share of total net equity in percentage points. */
  @Column(name = "actual_percentage")
  private Double actualPercentage;

  /** Actual minus target, in percentage points. */
  @Column(name = "deviation_percentage")
  private Double deviationPercentage;

  /** Target value of the hierarchy node in tenant currency. */
  @Column(name = "target_amount")
  private Double targetAmount;

  /** Actual value of the hierarchy node in tenant currency. */
  @Column(name = "actual_amount")
  private Double actualAmount;

  /** Direction of the proposed trade, not the direction of the exposure. */
  @Enumerated(EnumType.STRING)
  @Basic(optional = false)
  @Column(name = "recommended_action")
  private AlgoRecommendationAction recommendedAction;

  /** Monetary size of the proposed trade in tenant currency; null when nothing is proposed. */
  @Column(name = "recommended_amount")
  private Double recommendedAmount;

  /** Units of the proposed trade; null when the instrument has no usable price. */
  @Column(name = "recommended_units")
  private Double recommendedUnits;

  /** Evaluation mechanism that produced the recommendation. */
  @Enumerated(EnumType.STRING)
  @Basic(optional = false)
  @Column(name = "trigger_kind")
  private AlgoRebalancingTrigger triggerKind;

  /**
   * Why the line reads as it does, as a locale-independent token such as {@code REBALANCE_EXPOSURE_BREACH} or
   * {@code REBALANCE_TACTICAL_CEILING}. The client translates it; storing a rendered sentence would freeze the language
   * of whoever happened to trigger the evaluation.
   */
  @Column(name = "rationale")
  private String rationale;

  public AlgoRecommendation() {
  }

  @Override
  public Integer getId() {
    return idAlgoRecommendation;
  }

  public Integer getIdAlgoRecommendation() {
    return idAlgoRecommendation;
  }

  public void setIdAlgoRecommendation(Integer idAlgoRecommendation) {
    this.idAlgoRecommendation = idAlgoRecommendation;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Integer getIdAlgoTop() {
    return idAlgoTop;
  }

  public void setIdAlgoTop(Integer idAlgoTop) {
    this.idAlgoTop = idAlgoTop;
  }

  public Integer getIdAlgoStrategy() {
    return idAlgoStrategy;
  }

  public void setIdAlgoStrategy(Integer idAlgoStrategy) {
    this.idAlgoStrategy = idAlgoStrategy;
  }

  public String getLevelType() {
    return levelType;
  }

  public void setLevelType(String levelType) {
    this.levelType = levelType;
  }

  public Integer getIdNode() {
    return idNode;
  }

  public void setIdNode(Integer idNode) {
    this.idNode = idNode;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public LocalDate getRunDate() {
    return runDate;
  }

  public void setRunDate(LocalDate runDate) {
    this.runDate = runDate;
  }

  public LocalDate getCheckpointDate() {
    return checkpointDate;
  }

  public void setCheckpointDate(LocalDate checkpointDate) {
    this.checkpointDate = checkpointDate;
  }

  public LocalDate getValuationDate() {
    return valuationDate;
  }

  public void setValuationDate(LocalDate valuationDate) {
    this.valuationDate = valuationDate;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Double getTargetPercentage() {
    return targetPercentage;
  }

  public void setTargetPercentage(Double targetPercentage) {
    this.targetPercentage = targetPercentage;
  }

  public Double getActualPercentage() {
    return actualPercentage;
  }

  public void setActualPercentage(Double actualPercentage) {
    this.actualPercentage = actualPercentage;
  }

  public Double getDeviationPercentage() {
    return deviationPercentage;
  }

  public void setDeviationPercentage(Double deviationPercentage) {
    this.deviationPercentage = deviationPercentage;
  }

  public Double getTargetAmount() {
    return targetAmount;
  }

  public void setTargetAmount(Double targetAmount) {
    this.targetAmount = targetAmount;
  }

  public Double getActualAmount() {
    return actualAmount;
  }

  public void setActualAmount(Double actualAmount) {
    this.actualAmount = actualAmount;
  }

  public AlgoRecommendationAction getRecommendedAction() {
    return recommendedAction;
  }

  public void setRecommendedAction(AlgoRecommendationAction recommendedAction) {
    this.recommendedAction = recommendedAction;
  }

  public Double getRecommendedAmount() {
    return recommendedAmount;
  }

  public void setRecommendedAmount(Double recommendedAmount) {
    this.recommendedAmount = recommendedAmount;
  }

  public Double getRecommendedUnits() {
    return recommendedUnits;
  }

  public void setRecommendedUnits(Double recommendedUnits) {
    this.recommendedUnits = recommendedUnits;
  }

  public AlgoRebalancingTrigger getTriggerKind() {
    return triggerKind;
  }

  public void setTriggerKind(AlgoRebalancingTrigger triggerKind) {
    this.triggerKind = triggerKind;
  }

  public String getRationale() {
    return rationale;
  }

  public void setRationale(String rationale) {
    this.rationale = rationale;
  }

}
