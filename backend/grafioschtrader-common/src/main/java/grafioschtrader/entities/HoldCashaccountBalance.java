package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * It is a snapshot for the cash account. When a relevant transaction takes place on it, a period is opened and the
 * previously opened period is closed with it.
 * <p>
 * Each record corresponds to a period in which one or more cash-account transactions occurred, categorizing the net
 * effect of deposits/withdrawals, interest, fees, security buys/sells, and dividends. The balance reflects the
 * end-of-period cash balance, with currency conversion factors applied via embedded currency pair references for tenant
 * and portfolio.
 * <p>
 * This entity maps to the <code>hold_cashaccount_balance</code> table and is not exposed via the public REST API.
 */
@Entity
@Table(name = "hold_cashaccount_balance")
public class HoldCashaccountBalance extends HoldBase {

  /**
   * Composite key combining security cash account ID and start of hold period.
   */
  @EmbeddedId
  private HoldCashaccountBalanceKey idEm;

  /**
   * Currency pair ID for converting tenant base currency.
   */
  @Column(name = "id_currency_pair_tenant")
  private Integer idCurrencypairTenant;

  /**
   * Currency pair ID for converting portfolio base currency.
   */
  @Column(name = "id_currency_pair_portfolio")
  private Integer idCurrencypairPortoflio;

  /**
   * Net amount of withdrawals (negative) and deposits (positive) during the period.
   */
  @Column(name = "withdrawl_deposit")
  private Double withdrawlDeposit;

  /**
   * Total interest credited to the cash account during the period.
   */
  @Column(name = "interest_cashaccount")
  private Double interestCashaccount;

  /**
   * Total fees debited from the cash account during the period.
   */
  @Column(name = "fee")
  private Double fee;

  /**
   * Net effect of security buy (accumulate) or sell (reduce) transactions, converted to cash and varying daily with
   * exchange rates.
   */
  @Column(name = "accumulate_reduce")
  private Double accumulateReduce;

  /**
   * Total dividends credited to the cash account during the period.
   */
  @Column(name = "dividend")
  private Double dividend;

  /**
   * End-of-period cash balance after applying all transactions and conversions.
   */
  @Column(name = "balance")
  private double balance;

  /**
   * Default constructor for JPA.
   */
  public HoldCashaccountBalance() {
  }

  /**
   * Constructs a HoldCashaccountBalance with full transaction aggregates and conversion IDs.
   *
   * @param idTenant                the tenant ID
   * @param idPortfolio             the portfolio ID
   * @param idCashaccount           the security cash account ID
   * @param fromHoldDate            the start date of the hold period
   * @param withdrawlDeposit        net deposits/withdrawals
   * @param interestCashaccount     interest amount
   * @param fee                     fees amount
   * @param accumulateReduce        net accumulate/reduce amount
   * @param dividend                dividends amount
   * @param balance                 end-of-period balance
   * @param idCurrencypairTenant    tenant currency pair ID
   * @param idCurrencypairPortoflio portfolio currency pair ID
   */
  public HoldCashaccountBalance(Integer idTenant, Integer idPortfolio, Integer idCashaccount, LocalDate fromHoldDate,
      Double withdrawlDeposit, Double interestCashaccount, Double fee, Double accumulateReduce, Double dividend,
      double balance, Integer idCurrencypairTenant, Integer idCurrencypairPortoflio) {
    super(idTenant, idPortfolio);
    this.idEm = new HoldCashaccountBalanceKey(idCashaccount, fromHoldDate);
    this.withdrawlDeposit = withdrawlDeposit;
    this.interestCashaccount = interestCashaccount;
    this.fee = fee;
    this.accumulateReduce = accumulateReduce;
    this.dividend = dividend;
    this.balance = balance;
    this.idCurrencypairTenant = idCurrencypairTenant;
    this.idCurrencypairPortoflio = idCurrencypairPortoflio;
  }

  /**
   * @return the composite key of cash account and hold start date
   */
  public HoldCashaccountBalanceKey getIdEm() {
    return idEm;
  }

  /**
   * @return tenant currency pair ID
   */
  public Integer getIdCurrencypairTenant() {
    return idCurrencypairTenant;
  }

  /**
   * @return portfolio currency pair ID
   */
  public Integer getIdCurrencypairPortoflio() {
    return idCurrencypairPortoflio;
  }

  /**
   * @return net withdrawal/deposit total
   */
  public Double getWithdrawlDeposit() {
    return withdrawlDeposit;
  }

  /**
   * @return interest amount for period
   */
  public Double getInterestCashaccount() {
    return interestCashaccount;
  }

  /**
   * @return fee amount for period
   */
  public Double getFee() {
    return fee;
  }

  /**
   * @return net accumulate/reduce amount
   */
  public Double getAccumulateReduce() {
    return accumulateReduce;
  }

  /**
   * @return dividend amount for period
   */
  public Double getDividend() {
    return dividend;
  }

  /**
   * @return end-of-period balance
   */
  public double getBalance() {
    return balance;
  }

  /**
   * @param balance end-of-period balance to set
   */
  public void setBalance(double balance) {
    this.balance = balance;
  }

  /**
   * @param idCurrencypairTenant tenant currency pair ID to set
   */
  public void setIdCurrencypairTenant(Integer idCurrencypairTenant) {
    this.idCurrencypairTenant = idCurrencypairTenant;
  }

  /**
   * @param idCurrencypairPortoflio portfolio currency pair ID to set
   */
  public void setIdCurrencypairPortoflio(Integer idCurrencypairPortoflio) {
    this.idCurrencypairPortoflio = idCurrencypairPortoflio;
  }

  /**
   * Composite key class combining cash account ID and hold start date.
   */
  public static class HoldCashaccountBalanceKey implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The associated security cash account ID.
     */
    @Column(name = "id_securitycash_account")
    private Integer idSecuritycashAccount;

    /**
     * The start date of the hold period.
     */
    @Column(name = "from_hold_date")
    private LocalDate fromHoldDate;

    /**
     * Default constructor for JPA.
     */
    public HoldCashaccountBalanceKey() {
    }

    /**
     * Constructs the key with account ID and hold date.
     *
     * @param idSecuritycashAccount the cash account ID
     * @param fromHoldDate          the hold start date
     */
    public HoldCashaccountBalanceKey(Integer idSecuritycashAccount, LocalDate fromHoldDate) {
      this.idSecuritycashAccount = idSecuritycashAccount;
      this.fromHoldDate = fromHoldDate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      HoldCashaccountBalanceKey that = (HoldCashaccountBalanceKey) o;
      return Objects.equals(idSecuritycashAccount, that.idSecuritycashAccount)
          && Objects.equals(fromHoldDate, that.fromHoldDate);
    }

    @Override
    public int hashCode() {
      return Objects.hash(idSecuritycashAccount, fromHoldDate);
    }
  }
}
