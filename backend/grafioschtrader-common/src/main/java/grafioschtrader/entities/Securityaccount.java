/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;
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

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "share_use_until")
  @PropertyAlwaysUpdatable
  private LocalDate shareUseUntil;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "bond_use_until")
  @PropertyAlwaysUpdatable
  private LocalDate bondUseUntil;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "etf_use_until")
  @PropertyAlwaysUpdatable
  private LocalDate etfUseUntil;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "fond_use_until")
  @PropertyAlwaysUpdatable
  private LocalDate fondUseUntil;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "forex_use_until")
  @PropertyAlwaysUpdatable
  private LocalDate forexUseUntil;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "cfd_use_until")
  @PropertyAlwaysUpdatable
  private LocalDate cfdUseUntil;

  @JsonIgnore
  @Column(name = "weka_model")
  private byte[] wekaModel;

  @PropertyAlwaysUpdatable
  @Column(name = "lowest_transaction_cost")
  private Float lowestTransactionCost;

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

  public LocalDate getShareUseUntil() {
    return shareUseUntil;
  }

  public void setShareUseUntil(LocalDate shareUseUntil) {
    this.shareUseUntil = shareUseUntil;
  }

  public LocalDate getBondUseUntil() {
    return bondUseUntil;
  }

  public void setBondUseUntil(LocalDate bondUseUntil) {
    this.bondUseUntil = bondUseUntil;
  }

  public LocalDate getEtfUseUntil() {
    return etfUseUntil;
  }

  public void setEtfUseUntil(LocalDate etfUseUntil) {
    this.etfUseUntil = etfUseUntil;
  }

  public LocalDate getFondUseUntil() {
    return fondUseUntil;
  }

  public void setFondUseUntil(LocalDate fondUseUntil) {
    this.fondUseUntil = fondUseUntil;
  }

  public LocalDate getForexUseUntil() {
    return forexUseUntil;
  }

  public void setForexUseUntil(LocalDate forexUseUntil) {
    this.forexUseUntil = forexUseUntil;
  }

  public LocalDate getCfdUseUntil() {
    return cfdUseUntil;
  }

  public void setCfdUseUntil(LocalDate cfdUseUntil) {
    this.cfdUseUntil = cfdUseUntil;
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
