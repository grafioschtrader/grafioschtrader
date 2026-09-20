package grafioschtrader.entities;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import jakarta.persistence.*;

/**
 * Fill-derived position lifecycle. Exported with the tenant; runtime writes are bounded by the execution-state limit.
 *
 * <p>
 * {@code initialUnits} and {@code realizedExitUnits} carry the progress of a profit taking plan: the size the lifecycle
 * was opened with, and how much of it has already been given back. Both are projections of the ledger like every other
 * field here, so a tranche can neither execute twice nor be lost across a restart.
 * </p>
 */
@Entity
@Table(name = AlgoExecutionState.TABNAME)
public class AlgoExecutionState extends TenantBaseID {
  public static final String TABNAME = "algo_execution_state";

  /** Auto-generated primary key. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_algo_execution_state")
  private Integer idAlgoExecutionState;

  /** Tenant whose ledger produced this state. */
  @Column(name = "id_tenant")
  private Integer idTenant;

  /** Strategy controlling the position lifecycle. */
  @Column(name = "id_algo_strategy")
  private Integer idAlgoStrategy;

  /** Instrument held by the lifecycle. */
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  /** Monotonically increasing lifecycle number for successive positions in the same instrument. */
  @Column(name = "lifecycle")
  private long lifecycle;

  /** Current signed position units; positive for long positions and negative for short positions. */
  @Column(name = "signed_units")
  private double signedUnits;

  /** Price of the fill that opened this lifecycle. */
  @Column(name = "initial_price")
  private double initialPrice;

  /** Volume-weighted average price of the current position. */
  @Column(name = "average_price")
  private double averagePrice;

  /** Absolute position units immediately after the initial entry. */
  @Column(name = "initial_units")
  private double initialUnits;

  /** Units already sold by profit-taking tranches in this lifecycle. */
  @Column(name = "realized_exit_units")
  private double realizedExitUnits;

  /** Date of the most recent entry or add-on fill. */
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "last_entry")
  private LocalDate lastEntry;

  /** Date of the most recent exit or scale-out fill. */
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "last_exit")
  private LocalDate lastExit;

  @Override
  public Integer getId() {
    return idAlgoExecutionState;
  }

  public Integer getIdAlgoExecutionState() {
    return idAlgoExecutionState;
  }

  public void setIdAlgoExecutionState(Integer value) {
    idAlgoExecutionState = value;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public void setIdTenant(Integer value) {
    idTenant = value;
  }

  public Integer getIdAlgoStrategy() {
    return idAlgoStrategy;
  }

  public void setIdAlgoStrategy(Integer value) {
    idAlgoStrategy = value;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer value) {
    idSecuritycurrency = value;
  }

  public long getLifecycle() {
    return lifecycle;
  }

  public void setLifecycle(long value) {
    lifecycle = value;
  }

  public double getSignedUnits() {
    return signedUnits;
  }

  public void setSignedUnits(double value) {
    signedUnits = value;
  }

  public double getInitialPrice() {
    return initialPrice;
  }

  public void setInitialPrice(double value) {
    initialPrice = value;
  }

  public double getAveragePrice() {
    return averagePrice;
  }

  public void setAveragePrice(double value) {
    averagePrice = value;
  }

  public double getInitialUnits() {
    return initialUnits;
  }

  public void setInitialUnits(double value) {
    initialUnits = value;
  }

  public double getRealizedExitUnits() {
    return realizedExitUnits;
  }

  public void setRealizedExitUnits(double value) {
    realizedExitUnits = value;
  }

  public LocalDate getLastEntry() {
    return lastEntry;
  }

  public void setLastEntry(LocalDate value) {
    lastEntry = value;
  }

  public LocalDate getLastExit() {
    return lastExit;
  }

  public void setLastExit(LocalDate value) {
    lastExit = value;
  }
}
