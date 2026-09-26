package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafiosch.entities.TenantBaseID;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.types.AlgoSimulationRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Result of a historical replay. The row is pure system output of a simulation and is therefore deleted with its
 * environment and with the account, but never written to a personal data export: the target instance restores it by
 * running the simulation again.
 */
@Schema(description = """
    Definition, progress and metrics of one historical replay of a simulation environment. The effective strategy
    configuration, dates and calculation conventions are captured when the run is submitted so later edits cannot
    change its meaning. A repeat run replaces the environment's previous result. Metrics are present only for a
    completed run.""")
@Entity
@Table(name = AlgoSimulationResult.TABNAME)
public class AlgoSimulationResult extends TenantBaseID {

  public static final String TABNAME = "algo_simulation_result";

  @jakarta.persistence.Column(name = "apply_tax_models")
  private boolean applyTaxModels;

  public boolean isApplyTaxModels() {
    return applyTaxModels;
  }

  public void setApplyTaxModels(boolean applyTaxModels) {
    this.applyTaxModels = applyTaxModels;
  }

  @jakarta.persistence.Column(name = "generate_bond_coupons")
  private boolean generateBondCoupons;

  public boolean isGenerateBondCoupons() {
    return generateBondCoupons;
  }

  public void setGenerateBondCoupons(boolean generateBondCoupons) {
    this.generateBondCoupons = generateBondCoupons;
  }

  @jakarta.persistence.Column(name = "input_assumptions_json", columnDefinition = "LONGTEXT")
  private String inputAssumptionsJson;

  public String getInputAssumptionsJson() {
    return inputAssumptionsJson;
  }

  public void setInputAssumptionsJson(String inputAssumptionsJson) {
    this.inputAssumptionsJson = inputAssumptionsJson;
  }

  @jakarta.persistence.Column(name = "tax_income_summary_json", columnDefinition = "LONGTEXT")
  private String taxIncomeSummaryJson;

  public String getTaxIncomeSummaryJson() {
    return taxIncomeSummaryJson;
  }

  public void setTaxIncomeSummaryJson(String taxIncomeSummaryJson) {
    this.taxIncomeSummaryJson = taxIncomeSummaryJson;
  }

  @Schema(description = "Auto-generated primary key")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_simulation_result")
  private Integer idSimulationResult;

  @Schema(description = "The simulation environment the run belongs to, never the main tenant")
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "The AlgoTop the environment was created from")
  @Column(name = "id_algo_assetclass_security")
  private Integer idAlgoTop;

  @Schema(description = """
      Immutable opening date of the environment. Copied from the tenant when the run is submitted, so that the
      recorded run keeps its meaning.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "opening_date")
  private LocalDate openingDate;

  @Schema(description = "Last day the replay evaluates; always before the current day")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "end_date")
  private LocalDate endDate;

  @Schema(description = "Current lifecycle status of the replay")
  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private AlgoSimulationRunStatus status;

  @Schema(description = "Time at which replay processing started")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Schema(description = "Time at which replay processing completed, failed or was cancelled")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Schema(description = "Number of trading days the replay has to evaluate, known before the first one is evaluated")
  @Column(name = "trading_days_total")
  private int tradingDaysTotal;

  @Schema(description = "Trading days evaluated so far; together with the total this is the progress of a running job")
  @Column(name = "trading_days_done")
  private int tradingDaysDone;

  @Schema(description = """
      The strategy hierarchy at submit time in the shape of the hierarchy view: top level, asset classes, instruments and
      every strategy with its parameters. Later edits of the shared hierarchy cannot change it. Served by its own
      endpoint rather than with the polled run, and empty for a run submitted before the hierarchy was captured.""")
  @JsonIgnore
  @Column(name = "hierarchy_snapshot", columnDefinition = "LONGTEXT")
  private String hierarchySnapshot;

  @Schema(description = """
      Space separated message keys of the price, cost and metric conventions the run was calculated under, for
      example NEXT_CLOSE_FILL NO_TRANSACTION_COST. The result view resolves and lists them.""")
  @Column(name = "conventions", length = 1000)
  private String conventions;

  @Schema(description = "Return over the whole run as a decimal, 0.15 being 15 percent")
  @Column(name = "total_return")
  private Double totalReturn;

  @Schema(description = "Total return scaled to a calendar year, as a decimal")
  @Column(name = "annualized_return")
  private Double annualizedReturn;

  @Schema(description = "Largest peak to trough decline of the daily equity series, as a negative decimal")
  @Column(name = "max_drawdown")
  private Double maxDrawdown;

  @Schema(description = """
      Mean daily return divided by its standard deviation, annualized with the square root of 252 and calculated
      against a risk free rate of zero. Empty when fewer than two daily returns exist or they do not vary.""")
  @Column(name = "sharpe_ratio")
  private Double sharpeRatio;

  @Schema(description = "Closed round trips the replay produced; an open position at the end date counts for none")
  @Column(name = "total_trades")
  private Integer totalTrades;

  @Schema(description = "Closed round trips that produced a positive result")
  @Column(name = "winning_trades")
  private Integer winningTrades;

  @Schema(description = "Closed round trips that produced a negative result")
  @Column(name = "losing_trades")
  private Integer losingTrades;

  @Schema(description = "Why a cancelled or failed run stopped; empty for a completed one")
  @Column(name = "failure_message")
  private String failureMessage;

  @Schema(description = "Captured fallback payment delay in calendar days; null for legacy runs")
  @Column(name = "dividend_payment_delay_days")
  private Integer dividendPaymentDelayDays;

  @Schema(description = "Gross dividends paid during the run, converted to tenant currency at payment dates")
  @Column(name = "paid_dividends")
  private Double paidDividends;

  @Schema(description = "Unpaid dividend entitlements at the end date, valued in tenant currency")
  @Column(name = "dividend_receivables")
  private Double dividendReceivables;

  public Integer getDividendPaymentDelayDays() {
    return dividendPaymentDelayDays;
  }

  public void setDividendPaymentDelayDays(Integer days) {
    this.dividendPaymentDelayDays = days;
  }

  @Schema(description = "Economic FX markup paid in tenant currency; null until the run completes")
  @Column(name = "fx_markup_paid")
  private Double fxMarkupPaid;

  @Schema(description = "Accepted conversions without a tariff in an FX-modelled run; null until completion")
  @Column(name = "fx_uncovered_conversions")
  private Integer fxUncoveredConversions;

  public Double getFxMarkupPaid() {
    return roundAmount(fxMarkupPaid);
  }

  public void setFxMarkupPaid(Double amount) {
    this.fxMarkupPaid = amount;
  }

  public Integer getFxUncoveredConversions() {
    return fxUncoveredConversions;
  }

  public void setFxUncoveredConversions(Integer count) {
    this.fxUncoveredConversions = count;
  }

  public Double getPaidDividends() {
    return roundAmount(paidDividends);
  }

  public void setPaidDividends(Double amount) {
    this.paidDividends = amount;
  }

  public Double getDividendReceivables() {
    return roundAmount(dividendReceivables);
  }

  public void setDividendReceivables(Double amount) {
    this.dividendReceivables = amount;
  }

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

  public LocalDate getOpeningDate() {
    return openingDate;
  }

  public void setOpeningDate(LocalDate openingDate) {
    this.openingDate = openingDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public AlgoSimulationRunStatus getStatus() {
    return status;
  }

  public void setStatus(AlgoSimulationRunStatus status) {
    this.status = status;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public LocalDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(LocalDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  public int getTradingDaysTotal() {
    return tradingDaysTotal;
  }

  public void setTradingDaysTotal(int tradingDaysTotal) {
    this.tradingDaysTotal = tradingDaysTotal;
  }

  public int getTradingDaysDone() {
    return tradingDaysDone;
  }

  public void setTradingDaysDone(int tradingDaysDone) {
    this.tradingDaysDone = tradingDaysDone;
  }

  public String getHierarchySnapshot() {
    return hierarchySnapshot;
  }

  public void setHierarchySnapshot(String hierarchySnapshot) {
    this.hierarchySnapshot = hierarchySnapshot;
  }

  public String getConventions() {
    return conventions;
  }

  public void setConventions(String conventions) {
    this.conventions = conventions;
  }

  public Double getTotalReturn() {
    return roundPercentage(totalReturn);
  }

  public void setTotalReturn(Double totalReturn) {
    this.totalReturn = totalReturn;
  }

  public Double getAnnualizedReturn() {
    return roundPercentage(annualizedReturn);
  }

  public void setAnnualizedReturn(Double annualizedReturn) {
    this.annualizedReturn = annualizedReturn;
  }

  public Double getMaxDrawdown() {
    return roundPercentage(maxDrawdown);
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

  public String getFailureMessage() {
    return failureMessage;
  }

  public void setFailureMessage(String failureMessage) {
    this.failureMessage = failureMessage;
  }

  /** Keeps the API ratio unit: two extra fraction digits represent two decimal places after conversion to percent. */
  private static Double roundPercentage(Double value) {
    return value == null ? null : DataHelper.round(value, GlobalConstants.FID_PERCENTAGE_FRACTION + 2);
  }

  private static Double roundAmount(Double value) {
    return value == null ? null : DataBusinessHelper.roundStandard(value);
  }
}
