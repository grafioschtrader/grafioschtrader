package grafioschtrader.entities;

import java.io.Serializable;
import java.sql.Timestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
 * Stores aggregated performance metrics from a simulation run. Includes return, drawdown, Sharpe ratio, and trade
 * statistics.
 */
@Schema(description = "Simulation result metrics for a specific algo top configuration")
@Entity
@Table(name = AlgoSimulationResult.TABNAME)
public class AlgoSimulationResult extends TenantBaseID implements Serializable {

  public static final String TABNAME = "algo_simulation_result";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_simulation_result")
  private Integer idSimulationResult;

  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Basic(optional = false)
  @Column(name = "id_algo_top")
  private Integer idAlgoTop;

  @Schema(description = "Cumulative return over the simulation period as a decimal (e.g. 0.15 = 15%)")
  @Column(name = "total_return")
  private Double totalReturn;

  @Schema(description = "Annualized return normalized to a 252-trading-day year, as a decimal")
  @Column(name = "annualized_return")
  private Double annualizedReturn;

  @Schema(description = "Maximum peak-to-trough decline during the simulation, as a negative decimal (e.g. -0.12 = -12%)")
  @Column(name = "max_drawdown")
  private Double maxDrawdown;

  @Schema(description = "Sharpe ratio: (annualized return - risk-free rate) / annualized standard deviation of returns")
  @Column(name = "sharpe_ratio")
  private Double sharpeRatio;

  @Schema(description = "Total number of round-trip trades executed during the simulation")
  @Column(name = "total_trades")
  private Integer totalTrades;

  @Schema(description = "Number of trades that resulted in a profit")
  @Column(name = "winning_trades")
  private Integer winningTrades;

  @Schema(description = "Number of trades that resulted in a loss")
  @Column(name = "losing_trades")
  private Integer losingTrades;

  @Schema(description = "Auto-maintained timestamp when the simulation result was computed")
  @Column(name = "calculated_at", insertable = false, updatable = false)
  private Timestamp calculatedAt;

  @JsonIgnore
  @Override
  public Integer getId() {
    return idSimulationResult;
  }

  public Integer getIdSimulationResult() {
    return idSimulationResult;
  }

  public void setIdSimulationResult(Integer idSimulationResult) {
    this.idSimulationResult = idSimulationResult;
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

  public Double getTotalReturn() {
    return totalReturn;
  }

  public void setTotalReturn(Double totalReturn) {
    this.totalReturn = totalReturn;
  }

  public Double getAnnualizedReturn() {
    return annualizedReturn;
  }

  public void setAnnualizedReturn(Double annualizedReturn) {
    this.annualizedReturn = annualizedReturn;
  }

  public Double getMaxDrawdown() {
    return maxDrawdown;
  }

  public void setMaxDrawdown(Double maxDrawdown) {
    this.maxDrawdown = maxDrawdown;
  }

  public Double getSharpeRatio() {
    return sharpeRatio;
  }

  public void setSharpeRatio(Double sharpeRatio) {
    this.sharpeRatio = sharpeRatio;
  }

  public Integer getTotalTrades() {
    return totalTrades;
  }

  public void setTotalTrades(Integer totalTrades) {
    this.totalTrades = totalTrades;
  }

  public Integer getWinningTrades() {
    return winningTrades;
  }

  public void setWinningTrades(Integer winningTrades) {
    this.winningTrades = winningTrades;
  }

  public Integer getLosingTrades() {
    return losingTrades;
  }

  public void setLosingTrades(Integer losingTrades) {
    this.losingTrades = losingTrades;
  }

  public Timestamp getCalculatedAt() {
    return calculatedAt;
  }
}
