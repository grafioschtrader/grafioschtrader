package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Contains how much was paid into or out of a particular account. The relevant
 * transactions are represented as time frames with from/to and the
 * corresponding balance of the deposit or withdrawal.
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = "hold_cashaccount_deposit")
public class HoldCashaccountDeposit extends HoldBase {

  @EmbeddedId
  private HoldCashaccountDepositKey holdCashaccountKey;

  @Column(name = "deposit")
  private double deposit;

  @Column(name = "deposit_portfolio_currency")
  private double depositPortfolioCurrency;

  @Column(name = "deposit_tenant_currency")
  private double depositTenantCurrency;

  public HoldCashaccountDeposit() {
    super();
  }

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
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
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
