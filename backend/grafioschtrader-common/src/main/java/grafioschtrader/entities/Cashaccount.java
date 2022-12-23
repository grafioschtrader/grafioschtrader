package grafioschtrader.entities;

import java.io.Serializable;
import java.util.Date;
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
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = Cashaccount.TABNAME)
@DiscriminatorValue("C")
@Schema(description = "Contains the base data of a security or cash account")
public class Cashaccount extends Securitycashaccount implements Serializable {

  public static final String TABNAME = "cashaccount";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  private String currency;

  @JsonIgnore
  @OneToMany(mappedBy = "cashaccount", fetch = FetchType.LAZY)
  private List<Transaction> transactionList;

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

  public Integer getConnectIdSecurityaccount() {
    return connectIdSecurityaccount;
  }

  public void setConnectIdSecurityaccount(Integer connectIdSecurityaccount) {
    this.connectIdSecurityaccount = connectIdSecurityaccount;
  }

  public double calculateBalanceOnTransactions(final Date untilDatePlus) {
    double balance = 0.0;
    return balance
        + transactionList.stream().filter(transaction -> transaction.getTransactionTime().before(untilDatePlus))
            .mapToDouble(transaction -> transaction.getCashaccountAmount()).sum();
  }

  public void updateThis(Cashaccount sourceCashaccount) {
    this.setName(sourceCashaccount.getName());
    if (!this.getCurrency().equals(sourceCashaccount.getCurrency()) && this.getTransactionList().size() == 0) {
      // Only accept a change of currency when there is no transaction
      this.setCurrency(sourceCashaccount.getCurrency());
    }
    this.setNote(sourceCashaccount.getNote());
    this.setConnectIdSecurityaccount(sourceCashaccount.connectIdSecurityaccount);
  }

}
