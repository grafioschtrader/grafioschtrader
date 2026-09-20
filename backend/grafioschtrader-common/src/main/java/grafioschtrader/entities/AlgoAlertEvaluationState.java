package grafioschtrader.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.entities.TenantBaseID;
import jakarta.persistence.*;

/**
 * Scheduling and diagnostic state for one tenant/strategy/security pair, independent of crossing bounds. Only the
 * evaluator writes these rows; no REST write path exists. The unique pair key and cleanup of removed scopes bound
 * growth to configured alert/security pairs, whose parent entities already have limits. Export and account deletion
 * include this state. Expired leases are reclaimable after a restart.
 */
@Entity
@Table(name = AlgoAlertEvaluationState.TABNAME)
public class AlgoAlertEvaluationState extends TenantBaseID {
  public static final String TABNAME = "algo_alert_evaluation_state";

  /** Auto-generated primary key. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_algo_alert_evaluation_state")
  private Integer idAlgoAlertEvaluationState;

  /** Tenant whose alert scope is evaluated. */
  @Column(name = "id_tenant")
  private Integer idTenant;

  /** Alert strategy being evaluated. */
  @Column(name = "id_algo_strategy")
  private Integer idAlgoStrategy;

  /** Instrument against which the strategy is evaluated. */
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  /** Fingerprint of the strategy configuration used for the recorded evaluation state. */
  @Column(name = "config_fingerprint", length = 64)
  private String configFingerprint;

  /** Time of the most recent evaluation attempt. */
  @Column(name = "last_attempt")
  private LocalDateTime lastAttempt;

  /** Time of the most recent successful evaluation. */
  @Column(name = "last_success")
  private LocalDateTime lastSuccess;

  /** Timestamp of the market quote used by the most recent evaluation. */
  @Column(name = "quote_timestamp")
  private LocalDateTime quoteTimestamp;

  /** Most recent attempt to evaluate the instrument after its market close. */
  @Column(name = "closing_attempt")
  private LocalDateTime closingAttempt;

  /** Time until which an evaluator owns this state row. */
  @Column(name = "lease_until")
  private LocalDateTime leaseUntil;

  /** Token identifying the evaluator that owns the lease. */
  @Column(name = "lease_token", length = 36)
  private String leaseToken;

  /** Result category of the most recent evaluation attempt. */
  @Column(name = "outcome", length = 20)
  private String outcome;

  /** Diagnostic reason associated with the most recent outcome. */
  @Column(name = "reason", length = 1000)
  private String reason;

  @Override
  @JsonIgnore
  public Integer getId() {
    return idAlgoAlertEvaluationState;
  }

  public Integer getIdAlgoAlertEvaluationState() {
    return idAlgoAlertEvaluationState;
  }

  public void setIdAlgoAlertEvaluationState(Integer value) {
    this.idAlgoAlertEvaluationState = value;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public void setIdTenant(Integer value) {
    this.idTenant = value;
  }

  public Integer getIdAlgoStrategy() {
    return idAlgoStrategy;
  }

  public void setIdAlgoStrategy(Integer value) {
    this.idAlgoStrategy = value;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer value) {
    this.idSecuritycurrency = value;
  }

  public String getConfigFingerprint() {
    return configFingerprint;
  }

  public void setConfigFingerprint(String value) {
    this.configFingerprint = value;
  }

  public LocalDateTime getLastAttempt() {
    return lastAttempt;
  }

  public void setLastAttempt(LocalDateTime value) {
    this.lastAttempt = value;
  }

  public LocalDateTime getLastSuccess() {
    return lastSuccess;
  }

  public void setLastSuccess(LocalDateTime value) {
    this.lastSuccess = value;
  }

  public LocalDateTime getQuoteTimestamp() {
    return quoteTimestamp;
  }

  public void setQuoteTimestamp(LocalDateTime value) {
    this.quoteTimestamp = value;
  }

  public LocalDateTime getClosingAttempt() {
    return closingAttempt;
  }

  public void setClosingAttempt(LocalDateTime value) {
    this.closingAttempt = value;
  }

  public LocalDateTime getLeaseUntil() {
    return leaseUntil;
  }

  public void setLeaseUntil(LocalDateTime value) {
    this.leaseUntil = value;
  }

  public String getLeaseToken() {
    return leaseToken;
  }

  public void setLeaseToken(String value) {
    this.leaseToken = value;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String value) {
    this.outcome = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }
}
