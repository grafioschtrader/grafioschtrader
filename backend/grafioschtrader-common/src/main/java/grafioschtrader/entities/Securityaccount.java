/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertyAlwaysUpdatable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = Securityaccount.TABNAME)
@DiscriminatorValue("S")
public class Securityaccount extends Securitycashaccount implements Serializable {

  public static final String TABNAME = "securityaccount";

  private static final long serialVersionUID = 1L;

  @JsonIgnore
  @JoinColumn(name = "id_security_account")
  @OneToMany(fetch = FetchType.LAZY)
  @OrderBy("transactionTime ASC")
  private List<Transaction> securityTransactionList;

  @OneToOne(fetch = FetchType.EAGER)
  @JoinColumn(nullable = false, name = "id_trading_platform_plan")
  @PropertyAlwaysUpdatable
  private TradingPlatformPlan tradingPlatformPlan;

  @PropertyAlwaysUpdatable
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "id_securitycash_account", nullable = false)
  private List<SecaccountTradingPeriod> tradingPeriods = new ArrayList<>();

  @JsonIgnore
  @Column(name = "weka_model")
  private byte[] wekaModel;

  @PropertyAlwaysUpdatable
  @Column(name = "lowest_transaction_cost")
  private Float lowestTransactionCost;

  @Schema(description = """
      Optional YAML-based fee model that overrides the TradingPlatformPlan's fee model
      for this specific security account. When non-null and non-blank, this takes priority.""")
  @Column(name = "fee_model_yaml", columnDefinition = "TEXT")
  @PropertyAlwaysUpdatable
  private String feeModelYaml;

  @Transient
  private boolean hasTransaction;

  public Securityaccount() {
  }

  public Securityaccount(String name, Portfolio portfolio) {
    super(name, portfolio);
  }

  public List<Transaction> getSecurityTransactionList() {
    return securityTransactionList;
  }

  public void setSecurityTransactionList(List<Transaction> securityTransactionList) {
    this.securityTransactionList = securityTransactionList;
  }

  public TradingPlatformPlan getTradingPlatformPlan() {
    return tradingPlatformPlan;
  }

  public void setTradingPlatformPlan(TradingPlatformPlan tradingPlatformPlan) {
    this.tradingPlatformPlan = tradingPlatformPlan;
  }

  public List<SecaccountTradingPeriod> getTradingPeriods() {
    return tradingPeriods;
  }

  public void setTradingPeriods(List<SecaccountTradingPeriod> tradingPeriods) {
    this.tradingPeriods.clear();
    if (tradingPeriods != null) {
      this.tradingPeriods.addAll(tradingPeriods);
    }
  }

  /**
   * Replaces the trading periods collection reference with a plain ArrayList. Must be used instead of
   * {@link #setTradingPeriods} when re-persisting a detached entity after {@code em.clear()}, because
   * the setter preserves the Hibernate PersistentBag reference (required for orphanRemoval tracking),
   * which causes "Don't change the reference to a collection with delete-orphan enabled" when the
   * entity is persisted in a new persistence context.
   */
  public void replaceTradingPeriods(List<SecaccountTradingPeriod> tradingPeriods) {
    this.tradingPeriods = tradingPeriods != null ? new ArrayList<>(tradingPeriods) : new ArrayList<>();
  }

  public byte[] getWekaModel() {
    return wekaModel;
  }

  public void setWekaModel(byte[] wekaModel) {
    this.wekaModel = wekaModel;
  }

  public Float getLowestTransactionCost() {
    return lowestTransactionCost;
  }

  public void setLowestTransactionCost(Float lowestTransactionCost) {
    this.lowestTransactionCost = lowestTransactionCost;
  }

  public String getFeeModelYaml() {
    return feeModelYaml;
  }

  public void setFeeModelYaml(String feeModelYaml) {
    this.feeModelYaml = feeModelYaml;
  }

  public boolean isHasTransaction() {
    return hasTransaction;
  }

  public void setHasTransaction(boolean hasTransaction) {
    this.hasTransaction = hasTransaction;
  }

  @Override
  public String toString() {
    return "Securityaccount [idSecuritycashAccount=" + idSecuritycashAccount + "]";
  }

}
