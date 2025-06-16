package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Entity tracking cash deposits and withdrawals in account cash balances over time periods.
 * 
 * <p>
 * <strong>Cash Flow Tracking:</strong>
 * </p>
 * <p>
 * This entity records how much money was deposited into or withdrawn from specific cash accounts. It provides
 * time-based tracking of cash movements, representing deposit/withdrawal transactions as time frames with corresponding
 * balance changes.
 * </p>
 * 
 * <p>
 * <strong>Multi-Currency Support:</strong>
 * </p>
 * <p>
 * Cash deposits are tracked in three currency contexts to support comprehensive portfolio analysis:
 * </p>
 * <ul>
 * <li><strong>Account Currency</strong> - The native currency of the cash account</li>
 * <li><strong>Portfolio Currency</strong> - Converted to the portfolio's base currency</li>
 * <li><strong>Tenant Currency</strong> - Converted to the tenant's global base currency</li>
 * </ul>
 * 
 * <p>
 * <strong>Time-Frame Management:</strong>
 * </p>
 * <p>
 * Each record represents a specific time period during which the deposit/withdrawal balance remained constant. New
 * records are created when additional cash transactions occur, and previous records receive end dates to maintain
 * temporal continuity.
 * </p>
 * 
 * <p>
 * <strong>Balance Interpretation:</strong>
 * </p>
 * <ul>
 * <li><strong>Positive Values</strong> - Net deposits (money added to account)</li>
 * <li><strong>Negative Values</strong> - Net withdrawals (money removed from account)</li>
 * <li><strong>Zero Values</strong> - No net cash movement during the period</li>
 * </ul>
 * 
 * <p>
 * <strong>Portfolio Performance Analysis:</strong>
 * </p>
 * <p>
 * This data is essential for calculating accurate portfolio performance metrics, as it distinguishes between investment
 * gains/losses and external cash contributions/withdrawals. Performance calculations must account for cash flows to
 * determine true investment returns.
 * </p>
 * 
 * <p>
 * <strong>Data Access:</strong>
 * </p>
 * <p>
 * This table is not exposed via REST API as it represents internal calculation state used for portfolio analysis,
 * performance calculations, and cash flow reporting.
 * </p>
 */
@Entity
@Table(name = "hold_cashaccount_deposit")
public class HoldCashaccountDeposit extends HoldBase {

  /**
   * Composite primary key containing cash account identifier and deposit period start date. This key ensures unique
   * identification of deposit periods for each account.
   */
  @EmbeddedId
  private HoldCashaccountDepositKey holdCashaccountKey;

  /**
   * Net deposit amount in the account's native currency during this time period.
   * 
   * <p>
   * This value represents the cumulative net cash flow (deposits minus withdrawals) that occurred during this holding
   * period, expressed in the cash account's native currency.
   * </p>
   * 
   * <p>
   * <strong>Value Interpretation:</strong>
   * </p>
   * <ul>
   * <li>Positive: Net money deposited into the account</li>
   * <li>Negative: Net money withdrawn from the account</li>
   * <li>Zero: No net cash movement during this period</li>
   * </ul>
   */
  @Column(name = "deposit")
  private double deposit;

  /**
   * Deposit amount converted to the portfolio's base currency.
   * 
   * <p>
   * This field contains the same deposit amount as the {@code deposit} field, but converted to the portfolio's base
   * currency using the exchange rate applicable during the deposit period. This enables portfolio-level cash flow
   * analysis when accounts use different currencies.
   * </p>
   * 
   * <p>
   * If the account currency matches the portfolio currency, this value will be identical to the {@code deposit} field.
   * </p>
   */
  @Column(name = "deposit_portfolio_currency")
  private double depositPortfolioCurrency;

  /**
   * Deposit amount converted to the tenant's global base currency.
   * 
   * <p>
   * This field contains the deposit amount converted to the tenant's global base currency using the exchange rate
   * applicable during the deposit period. This enables tenant-wide cash flow analysis and consolidated reporting across
   * all portfolios and currencies.
   * </p>
   * 
   * <p>
   * If the account currency matches the tenant currency, this value will be identical to the {@code deposit} field.
   * </p>
   */
  @Column(name = "deposit_tenant_currency")
  private double depositTenantCurrency;

  public HoldCashaccountDeposit() {
    super();
  }

  /**
   * Creates a new cash deposit holding record with specified parameters.
   * 
   * <p>
   * This constructor initializes a complete deposit record including multi-currency values and time period boundaries.
   * The portfolio currency deposit amount should be calculated and provided by the caller.
   * </p>
   * 
   * @param idTenant              the tenant identifier (inherited from HoldBase)
   * @param idPortfolio           the portfolio identifier (inherited from HoldBase)
   * @param idSecuritycashAccount the cash account identifier
   * @param fromHoldDate          the start date of this deposit period
   * @param toHoldDate            the end date of this deposit period (inherited from HoldBase)
   * @param balance               the net deposit amount in account currency
   * @param balanceTenantCurrency the deposit amount in tenant currency
   */
  public HoldCashaccountDeposit(Integer idTenant, Integer idPortfolio, Integer idSecuritycashAccount,
      LocalDate fromHoldDate, LocalDate toHoldDate, double balance, double balanceTenantCurrency) {
    super(idTenant, idPortfolio, toHoldDate);
    this.holdCashaccountKey = new HoldCashaccountDepositKey(idSecuritycashAccount, fromHoldDate);
    this.deposit = balance;
    this.depositTenantCurrency = balanceTenantCurrency;
  }

  public HoldCashaccountDepositKey getHoldCashaccountKey() {
    return holdCashaccountKey;
  }

  public void setHoldCashaccountKey(HoldCashaccountDepositKey holdCashaccountKey) {
    this.holdCashaccountKey = holdCashaccountKey;
  }

  public double getDeposit() {
    return deposit;
  }

  public void setDeposit(double deposit) {
    this.deposit = deposit;
  }

  public double getDepositPortfolioCurrency() {
    return depositPortfolioCurrency;
  }

  public void setDepositPortfolioCurrency(double depositPortfolioCurrency) {
    this.depositPortfolioCurrency = depositPortfolioCurrency;
  }

  public double getDepositTenantCurrency() {
    return depositTenantCurrency;
  }

  public void setDepositTenantCurrency(double depositTenantCurrency) {
    this.depositTenantCurrency = depositTenantCurrency;
  }

  public LocalDate getFromHoldDate() {
    return holdCashaccountKey.getFromHoldDate();
  }

  @Override
  public String toString() {
    return "HoldCashaccountDeposit [holdCashaccountKey=" + holdCashaccountKey + ", toHoldDate=" + toHoldDate
        + ", deposit=" + deposit + ", depositPortfolioCurrency=" + depositPortfolioCurrency + ", depositTenantCurrency="
        + depositTenantCurrency + ", idTenant=" + idTenant + ", idPortfolio=" + idPortfolio + ", validTimestamp="
        + validTimestamp + "]";
  }

  public static class HoldCashaccountDepositKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_securitycash_account")
    private Integer idSecuritycashAccount;

    @Column(name = "from_hold_date")
    private LocalDate fromHoldDate;

    public HoldCashaccountDepositKey() {
    }

    public HoldCashaccountDepositKey(Integer idSecuritycashAccount, LocalDate fromHoldDate) {
      super();
      this.idSecuritycashAccount = idSecuritycashAccount;
      this.fromHoldDate = fromHoldDate;
    }

    public Integer getIdSecuritycashAccount() {
      return idSecuritycashAccount;
    }

    public LocalDate getFromHoldDate() {
      return fromHoldDate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      HoldCashaccountDepositKey that = (HoldCashaccountDepositKey) o;
      return Objects.equals(idSecuritycashAccount, that.idSecuritycashAccount)
          && Objects.equals(fromHoldDate, that.fromHoldDate);
    }

    @Override
    public int hashCode() {
      return Objects.hash(idSecuritycashAccount, fromHoldDate);
    }

    @Override
    public String toString() {
      return "HoldCashaccountDepositKey [idSecuritycashAccount=" + idSecuritycashAccount + ", fromHoldDate="
          + fromHoldDate + "]";
    }

  }

}
