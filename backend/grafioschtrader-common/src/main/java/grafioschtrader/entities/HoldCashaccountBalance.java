package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * It categorizes every transaction on the cash accounts by its transaction
 * type. That means for every day with one or more transaction produces a new
 * period. The number of records in this is less than the number of records in
 * transaction because multiple transaction on one day produce a single record.
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = "hold_cashaccount_balance")
public class HoldCashaccountBalance extends HoldBase {

  @EmbeddedId
  private HoldCashaccountBalanceKey idEm;

  @Column(name = "id_currency_pair_tenant")
  private Integer idCurrencypairTenant;

  @Column(name = "id_currency_pair_portfolio")
  private Integer idCurrencypairPortoflio;

  @Column(name = "withdrawl_deposit")
  private Double withdrawlDeposit;

  @Column(name = "interest_cashaccount")
  private Double interestCashaccount;

  @Column(name = "fee")
  private Double fee;

  /**
   * Accumulate or reduce the securities position, it may change daily because of
   * the changing exchange rate.
   */
  @Column(name = "accumulate_reduce")
  private Double accumulateReduce;

  @Column(name = "dividend")
  private Double dividend;

  @Column(name = "balance")
  private double balance;

  public HoldCashaccountBalance() {
  }

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

  public double getBalance() {
    return balance;
  }

  public Double getWithdrawlDeposit() {
    return withdrawlDeposit;
  }

  public Double getInterestCashaccount() {
    return interestCashaccount;
  }

  public Double getFee() {
    return fee;
  }

  public Double getAccumulateReduce() {
    return accumulateReduce;
  }

  public Double getDividend() {
    return dividend;
  }

  public void setBalance(double balance) {
    this.balance = balance;
  }

  public Integer getIdCurrencypairTenant() {
    return idCurrencypairTenant;
  }

  public void setIdCurrencypairTenant(Integer idCurrencypairTenant) {
    this.idCurrencypairTenant = idCurrencypairTenant;
  }

  public Integer getIdCurrencypairPortoflio() {
    return idCurrencypairPortoflio;
  }

  public void setIdCurrencypairPortoflio(Integer idCurrencypairPortoflio) {
    this.idCurrencypairPortoflio = idCurrencypairPortoflio;
  }

  public static class HoldCashaccountBalanceKey implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "id_securitycash_account")
    private Integer idSecuritycashAccount;

    @Column(name = "from_hold_date")
    private LocalDate fromHoldDate;

    public HoldCashaccountBalanceKey() {
    }

    public HoldCashaccountBalanceKey(Integer idSecuritycashAccount, LocalDate fromHoldDate) {
      super();
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
