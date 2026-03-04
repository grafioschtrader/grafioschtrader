package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-asset runtime state for simulation execution. Tracks position quantities, average costs, tranche state, and
 * add/sell history for each security within a simulation run. One row per (AlgoTop, security) combination, updated
 * as the simulation processes events chronologically.
 */
@Schema(description = """
    Per-asset runtime state for simulation execution. Tracks position quantities, costs, tranche state,
    and buy/sell history for each security within a simulation run.""")
@Entity
@Table(name = AlgoExecutionState.TABNAME)
public class AlgoExecutionState extends TenantBaseID implements Serializable {

  public static final String TABNAME = "algo_execution_state";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_algo_execution_state")
  private Integer idAlgoExecutionState;

  @Basic(optional = false)
  @Column(name = "id_algo_top")
  private Integer idAlgoTop;

  @Basic(optional = false)
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "Current number of units held (positive for long, negative for short)")
  @Column(name = "position_qty")
  private Double positionQty;

  @Schema(description = "Position direction: 1 = long, -1 = short, null = flat/no position")
  @Column(name = "position_direction")
  private Byte positionDirection;

  @Schema(description = "Volume-weighted average cost per unit of the current position")
  @Column(name = "avg_cost")
  private Double avgCost;

  @Schema(description = "Price at the first entry into this position")
  @Column(name = "initial_entry_price")
  private Double initialEntryPrice;

  @Schema(description = "Number of units bought at the initial entry")
  @Column(name = "initial_entry_qty")
  private Double initialEntryQty;

  @Schema(description = "Number of add-on buys (tranches) executed after the initial entry")
  @Column(name = "adds_done")
  private Integer addsDone;

  @Schema(description = "Date of the most recent buy transaction for this position")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "last_buy_date")
  private LocalDate lastBuyDate;

  @Schema(description = "Date of the most recent sell transaction for this position")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "last_sell_date")
  private LocalDate lastSellDate;

  @Schema(description = """
      JSON array tracking individual tranches, e.g.
      [{"qty": 10, "price": 50.0, "date": "2025-01-15"}, {"qty": 5, "price": 48.0, "date": "2025-02-01"}]""")
  @Column(name = "tranche_state", columnDefinition = "JSON")
  private String trancheState;

  @Schema(description = """
      JSON object with strategy-specific runtime data, e.g. indicator values, signal state,
      or custom counters that persist between evaluation cycles.""")
  @Column(name = "state_data", columnDefinition = "JSON")
  private String stateData;

  @Schema(description = "Auto-maintained timestamp of the last row update")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  @JsonIgnore
  @Override
  public Integer getId() {
    return idAlgoExecutionState;
  }

  public Integer getIdAlgoExecutionState() {
    return idAlgoExecutionState;
  }

  public void setIdAlgoExecutionState(Integer idAlgoExecutionState) {
    this.idAlgoExecutionState = idAlgoExecutionState;
  }

  public Integer getIdAlgoTop() {
    return idAlgoTop;
  }

  public void setIdAlgoTop(Integer idAlgoTop) {
    this.idAlgoTop = idAlgoTop;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Double getPositionQty() {
    return positionQty;
  }

  public void setPositionQty(Double positionQty) {
    this.positionQty = positionQty;
  }

  public Byte getPositionDirection() {
    return positionDirection;
  }

  public void setPositionDirection(Byte positionDirection) {
    this.positionDirection = positionDirection;
  }

  public Double getAvgCost() {
    return avgCost;
  }

  public void setAvgCost(Double avgCost) {
    this.avgCost = avgCost;
  }

  public Double getInitialEntryPrice() {
    return initialEntryPrice;
  }

  public void setInitialEntryPrice(Double initialEntryPrice) {
    this.initialEntryPrice = initialEntryPrice;
  }

  public Double getInitialEntryQty() {
    return initialEntryQty;
  }

  public void setInitialEntryQty(Double initialEntryQty) {
    this.initialEntryQty = initialEntryQty;
  }

  public Integer getAddsDone() {
    return addsDone;
  }

  public void setAddsDone(Integer addsDone) {
    this.addsDone = addsDone;
  }

  public LocalDate getLastBuyDate() {
    return lastBuyDate;
  }

  public void setLastBuyDate(LocalDate lastBuyDate) {
    this.lastBuyDate = lastBuyDate;
  }

  public LocalDate getLastSellDate() {
    return lastSellDate;
  }

  public void setLastSellDate(LocalDate lastSellDate) {
    this.lastSellDate = lastSellDate;
  }

  public String getTranchState() {
    return trancheState;
  }

  public void setTrancheState(String trancheState) {
    this.trancheState = trancheState;
  }

  public String getStateData() {
    return stateData;
  }

  public void setStateData(String stateData) {
    this.stateData = stateData;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
