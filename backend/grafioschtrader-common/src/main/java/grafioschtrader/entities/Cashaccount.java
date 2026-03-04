package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Contains the base data of a security or cash account")
@Entity
@Table(name = Cashaccount.TABNAME)
@DiscriminatorValue("C")
public class Cashaccount extends Securitycashaccount implements Serializable {

  public static final String TABNAME = "cashaccount";

  private static final long serialVersionUID = 1L;

  @Schema(description = "A cash account needs a currency, hence ISO 4217 for currency designation.")
  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  private String currency;

  @Schema(description = "In some cases, it is helpful to have direct access to the transactions.")
  @JsonIgnore
  @OneToMany(mappedBy = "cashaccount", fetch = FetchType.LAZY)
  private List<Transaction> transactionList;

  @Schema(description = """
      Annual borrowing rate in percent for overdraft control. NULL means the account may not have a negative
      balance. A value >= 0 means the account can be overdrawn at the specified annual interest rate.""")
  @DecimalMin("0.0")
  @Column(name = "borrowing_rate")
  private Double borrowingRate;

  @Schema(description = """
      This 'Deposit' input field should only contain a value if there are multiple deposits within a tenant.
      This prevents ambiguities in portfolio evaluations. Therefore, this assignment should be made if there
      are multiple deposits and bank accounts with the same currency.""")
  @Column(name = "connect_id_securityaccount")
  private Integer connectIdSecurityaccount;

  public Cashaccount() {
  }

  public Cashaccount(String name, Double balance, String currency, Portfolio portfolio) {
    super(name, portfolio);
    this.currency = currency;
  }

  public Cashaccount(Integer idSecuritycashAccount, String currency) {
    this.idSecuritycashAccount = idSecuritycashAccount;
    this.currency = currency;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public List<Transaction> getTransactionList() {
    return transactionList;
  }

  public void setTransactionList(List<Transaction> transactionList) {
    this.transactionList = transactionList;
  }

  public Double getBorrowingRate() {
    return borrowingRate;
  }

  public void setBorrowingRate(Double borrowingRate) {
    this.borrowingRate = borrowingRate;
  }

  public Integer getConnectIdSecurityaccount() {
    return connectIdSecurityaccount;
  }

  public void setConnectIdSecurityaccount(Integer connectIdSecurityaccount) {
    this.connectIdSecurityaccount = connectIdSecurityaccount;
  }

  public double calculateBalanceOnTransactions(final LocalDateTime untilDatePlus) {
    double balance = 0.0;
    return balance
        + transactionList.stream().filter(transaction -> transaction.getTransactionTime().isBefore(untilDatePlus))
            .mapToDouble(transaction -> transaction.getCashaccountAmount()).sum();
  }

  public void updateThis(Cashaccount sourceCashaccount) {
    this.setName(sourceCashaccount.getName());
    if (!this.getCurrency().equals(sourceCashaccount.getCurrency()) && this.getTransactionList().size() == 0) {
      // Only accept a change of currency when there is no transaction
      this.setCurrency(sourceCashaccount.getCurrency());
    }
    this.setNote(sourceCashaccount.getNote());
    this.setBorrowingRate(sourceCashaccount.getBorrowingRate());
    this.setConnectIdSecurityaccount(sourceCashaccount.connectIdSecurityaccount);
  }

}
