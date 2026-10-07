package grafioschtrader.entities;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * The total value of a tenant or of one of its portfolios at the close of one trading day: securities plus cash balance
 * plus the result of the closed margin positions, together with every other figure of the period performance.
 *
 * <p>
 * There is one row per trading day for each portfolio, in the portfolio currency, and one for the tenant with
 * {@code idPortfolio} null, in the tenant currency. The rows are copies of what
 * {@code HoldSecurityaccountSecurity.getPeriodHoldingsByTenant} / {@code ...ByPortfolio} deliver for that day, so they
 * match the period performance report by construction. A day the report cannot value completely, because a quote or an
 * exchange rate is missing, has no row either. They exist so that a consumer needing the value of many days, such as a
 * chart or a fee model graded by the total assets, does not have to run that query each time.
 * </p>
 *
 * <p>
 * The task {@code HOLD_DAILY_TOTAL_UPDATE} is the only writer: it deletes a range of days and inserts it again in one
 * transaction. That is why there is no unique key, which MariaDB would not enforce for a null {@code idPortfolio}
 * anyway. From which day the rows of a tenant may be missing or outdated is kept in {@link HoldDailyTotalState}.
 * </p>
 *
 * <p>
 * The table is derived data and deliberately not part of an {@code ExportDefinition}: after an import the tenant has no
 * {@link HoldDailyTotalState} row, which makes the task compute its whole history again. Deleting the tenant or a
 * portfolio removes the rows through the foreign key cascades. No entity limit applies, because nothing but the task
 * writes it.
 * </p>
 */
@Entity
@Table(name = HoldDailyTotal.TABNAME)
public class HoldDailyTotal implements IPeriodHolding {

  public static final String TABNAME = "hold_daily_total";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_hold_daily_total")
  private Integer idHoldDailyTotal;

  @Column(name = "id_tenant")
  private Integer idTenant;

  /** The portfolio, or null for the total of the tenant. */
  @Column(name = "id_portfolio")
  private Integer idPortfolio;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "hold_date")
  private LocalDate holdDate;

  @Column(name = "cash_balance_mc")
  private double cashBalanceMC;

  @Column(name = "securities_mc")
  private double securitiesMC;

  @Column(name = "margin_close_gain_mc")
  private double marginCloseGainMC;

  @Column(name = "security_risk_mc")
  private double securityRiskMC;

  @Column(name = "external_cash_transfer_mc")
  private double externalCashTransferMC;

  @Column(name = "dividend_real_mc")
  private double dividendRealMC;

  @Column(name = "fee_real_mc")
  private double feeRealMC;

  @Column(name = "interest_cashaccount_real_mc")
  private double interestCashaccountRealMC;

  @Column(name = "accumulate_reduce_mc")
  private double accumulateReduceMC;

  @Column(name = "gain_mc")
  private double gainMC;

  public HoldDailyTotal() {
  }

  /**
   * Copies one day of the period holdings.
   *
   * @param idTenant      the tenant the day belongs to
   * @param idPortfolio   the portfolio, or null when the row is the total of the tenant
   * @param periodHolding the day as delivered by the period holdings query
   */
  public HoldDailyTotal(Integer idTenant, Integer idPortfolio, IPeriodHolding periodHolding) {
    this.idTenant = idTenant;
    this.idPortfolio = idPortfolio;
    this.holdDate = periodHolding.getDate();
    this.cashBalanceMC = periodHolding.getCashBalanceMC();
    this.securitiesMC = periodHolding.getSecuritiesMC();
    this.marginCloseGainMC = periodHolding.getMarginCloseGainMC();
    this.securityRiskMC = periodHolding.getSecurityRiskMC();
    this.externalCashTransferMC = periodHolding.getExternalCashTransferMC();
    this.dividendRealMC = periodHolding.getDividendRealMC();
    this.feeRealMC = periodHolding.getFeeRealMC();
    this.interestCashaccountRealMC = periodHolding.getInterestCashaccountRealMC();
    this.accumulateReduceMC = periodHolding.getAccumulateReduceMC();
    this.gainMC = periodHolding.getGainMC();
  }

  public Integer getIdHoldDailyTotal() {
    return idHoldDailyTotal;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public Integer getIdPortfolio() {
    return idPortfolio;
  }

  public LocalDate getHoldDate() {
    return holdDate;
  }

  /** Same as {@link #getHoldDate()}, the name {@link IPeriodHolding} uses. */
  @Override
  @JsonIgnore
  public LocalDate getDate() {
    return holdDate;
  }

  @Override
  public double getCashBalanceMC() {
    return cashBalanceMC;
  }

  @Override
  public double getSecuritiesMC() {
    return securitiesMC;
  }

  @Override
  public double getMarginCloseGainMC() {
    return marginCloseGainMC;
  }

  @Override
  public double getSecurityRiskMC() {
    return securityRiskMC;
  }

  @Override
  public double getExternalCashTransferMC() {
    return externalCashTransferMC;
  }

  @Override
  public double getDividendRealMC() {
    return dividendRealMC;
  }

  @Override
  public double getFeeRealMC() {
    return feeRealMC;
  }

  @Override
  public double getInterestCashaccountRealMC() {
    return interestCashaccountRealMC;
  }

  @Override
  public double getAccumulateReduceMC() {
    return accumulateReduceMC;
  }

  @Override
  public double getGainMC() {
    return gainMC;
  }

  /**
   * The total value of the day: cash balance, securities and the result of the closed margin positions. Computed rather
   * than stored, in the same way as {@code PeriodHoldingAndDiff.getTotalBalanceMC()}.
   *
   * @return the total value in the currency of the portfolio or of the tenant
   */
  @Transient
  public double getTotalBalanceMC() {
    return DataBusinessHelper.roundStandard(cashBalanceMC + securitiesMC + marginCloseGainMC);
  }

}
