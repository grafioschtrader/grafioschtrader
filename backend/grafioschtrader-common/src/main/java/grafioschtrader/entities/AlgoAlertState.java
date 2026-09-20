package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.entities.TenantBaseID;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Remembers on which side of a configured bound an instrument last stood, so that an alert can notify on a crossing
 * rather than on a level. Without it a price that is already above its upper bound reports an alarm on the first
 * evaluation and on every one after, and a moving average alert degenerates into "the price is above the average".
 *
 * <p>
 * One row per tenant, strategy, instrument and bound. The bound is part of the key because a single alert can watch
 * more than one - an absolute price alert has a lower and an upper bound that cross independently - and the instrument
 * is part of it because one strategy on an AlgoTop or on an asset class bucket is evaluated against every instrument in
 * its scope.
 * </p>
 *
 * <p>
 * {@code configFingerprint} records the configuration the stored side was observed under. When it differs from the
 * current one - the user edited a threshold, or re-enabled an alert that was off while the price moved - the next
 * evaluation re-establishes a baseline instead of reporting a crossing that never happened under the configuration now
 * in force.
 * </p>
 *
 * <p>
 * The rows are derived state: losing them costs one suppressed notification per alert while the baselines are
 * re-established, and nothing else. They are therefore deliberately absent from the export definitions, which is why
 * they are neither written to nor read from a personal data export.
 * </p>
 */
@Entity
@Table(name = AlgoAlertState.TABNAME)
public class AlgoAlertState extends TenantBaseID implements Serializable {

  public static final String TABNAME = "algo_alert_state";

  private static final long serialVersionUID = 1L;

  /** Auto-generated primary key. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_algo_alert_state")
  private Integer idAlgoAlertState;

  /** Tenant whose alert crossing state is stored. */
  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  /** Alert strategy whose bound is tracked. */
  @Basic(optional = false)
  @Column(name = "id_algo_strategy")
  private Integer idAlgoStrategy;

  /** Instrument whose position relative to the bound is tracked. */
  @Basic(optional = false)
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  /** Stable identifier of the independently crossing bound within the strategy. */
  @Basic(optional = false)
  @Column(name = "bound_key")
  private String boundKey;

  /** Fingerprint of the configuration under which the stored side was observed. */
  @Basic(optional = false)
  @Column(name = "config_fingerprint")
  private String configFingerprint;

  /** -1 below the bound, 0 exactly at it, 1 above it. */
  @Basic(optional = false)
  @Column(name = "last_side")
  private byte lastSide;

  /** Value observed during the most recent evaluation. */
  @Column(name = "last_value")
  private Double lastValue;

  /** Time at which the bound was most recently evaluated. */
  @Column(name = "last_evaluated")
  private LocalDateTime lastEvaluated;

  public AlgoAlertState() {
  }

  public AlgoAlertState(Integer idTenant, Integer idAlgoStrategy, Integer idSecuritycurrency, String boundKey,
      String configFingerprint, byte lastSide, Double lastValue, LocalDateTime lastEvaluated) {
    this.idTenant = idTenant;
    this.idAlgoStrategy = idAlgoStrategy;
    this.idSecuritycurrency = idSecuritycurrency;
    this.boundKey = boundKey;
    this.configFingerprint = configFingerprint;
    this.lastSide = lastSide;
    this.lastValue = lastValue;
    this.lastEvaluated = lastEvaluated;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idAlgoAlertState;
  }

  public Integer getIdAlgoAlertState() {
    return idAlgoAlertState;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Integer getIdAlgoStrategy() {
    return idAlgoStrategy;
  }

  public void setIdAlgoStrategy(Integer idAlgoStrategy) {
    this.idAlgoStrategy = idAlgoStrategy;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public String getBoundKey() {
    return boundKey;
  }

  public void setBoundKey(String boundKey) {
    this.boundKey = boundKey;
  }

  public String getConfigFingerprint() {
    return configFingerprint;
  }

  public void setConfigFingerprint(String configFingerprint) {
    this.configFingerprint = configFingerprint;
  }

  public byte getLastSide() {
    return lastSide;
  }

  public void setLastSide(byte lastSide) {
    this.lastSide = lastSide;
  }

  public Double getLastValue() {
    return lastValue;
  }

  public void setLastValue(Double lastValue) {
    this.lastValue = lastValue;
  }

  public LocalDateTime getLastEvaluated() {
    return lastEvaluated;
  }

  public void setLastEvaluated(LocalDateTime lastEvaluated) {
    this.lastEvaluated = lastEvaluated;
  }

}
