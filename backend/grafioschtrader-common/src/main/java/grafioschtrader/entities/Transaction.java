package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.SecurityCostPosition;
import grafioschtrader.types.TransactionType;
import grafioschtrader.validation.AfterEqual;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

@Entity
@Table(name = Transaction.TABNAME)
@Schema(description = "Entity that contains the information for a single transaction. There are cash and securities transaction.")
public class Transaction extends TenantBaseID implements Serializable, Comparable<Transaction> {

  public static final String TABNAME = "transaction";
  private static final Logger log = LoggerFactory.getLogger(Transaction.class);

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_transaction")
  private Integer idTransaction;

  @Schema(description = "Quantity when buying and selling securities or the number of days when financing costs")
  @Basic(optional = true)
  @Column(name = "units")
  @NotNull(groups = { SecurityTransaction.class })
  @Positive(groups = { SecurityTransaction.class })
  @Null(groups = { CashTransaction.class })
  private Double units;

  @Schema(description = "Instrument price or dividend per unit respectively the financing costs per day")
  @Basic(optional = true)
  @Column(name = "quotation")
  @NotNull(groups = { SecurityTransaction.class })
  @Null(groups = { CashTransaction.class })
  private Double quotation;

  @Schema(description = "The transaction type")
  @Basic(optional = false)
  @Column(name = "transaction_type")
  @NotNull
  private byte transactionType;

  @Schema(description = "Account transfer transaction point to each other. Margin product close and finance cost position to margin open position.")
  @Column(name = "con_id_transaction")
  private Integer connectedIdTransaction;

  @Column(name = "taxable_interest", nullable = true, columnDefinition = "TINYINT", length = 1)
  private Boolean taxableInterest;

  /**
   * Taxes for a security or cash transaction (interest)
   */

  // @Max(value=?) @Min(value=?)//if you know range of your decimal fields
  // consider using these annotations to enforce field validation
  @Column(name = "tax_cost")
  private Double taxCost;

  @Column(name = "transaction_cost")
  private Double transactionCost;

  @Column(name = "note")
  @Size(max = GlobalConstants.FID_MAX_LETTERS)
  private String note;

  @Basic(optional = false)
  @Column(name = "cashaccount_amount")
  @NotNull
  private Double cashaccountAmount;

  @Basic(optional = false)
  @Column(name = "transaction_time")
  @Temporal(TemporalType.TIMESTAMP)
  @NotNull
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = GlobalConstants.STANDARD_DATE_FORMAT)
  private Date transactionTime;

  @Schema(description = "Transaction Date is set when an entity is saved")
  @JsonIgnore
  @Column(name = "tt_date")
  private LocalDate transactionDate;

  @Schema(description = "Sometimes the dividend is paid after a security has been sold, "
      + "in which case the dividend date must be given.")
  @Column(name = "ex_date")
  @Temporal(TemporalType.DATE)
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = GlobalConstants.STANDARD_DATE_FORMAT)
  @Null(groups = { CashTransaction.class })
  private Date exDate;

  @Schema(description = "Transaction on a security must be connected with a security account")
  @JoinColumn(name = "id_securitycurrency", referencedColumnName = "id_securitycurrency")
  @ManyToOne
  @NotNull(groups = { SecurityTransaction.class })
  @Null(groups = { CashTransaction.class })
  private Security security;

  @Schema(description = "Every transaction is connected with a cash account")
  // This reference is used for transaction removal
  @JoinColumn(name = "id_cash_account", referencedColumnName = "id_securitycash_account")
  @ManyToOne
  @NotNull
  private Cashaccount cashaccount;

  @Schema(description = "The currency exchange rate, it must be set with a currency pair")
  @Column(name = "currency_ex_rate")
  private Double currencyExRate;

  @Schema(description = "Id of currency pair is used in conjunction with currencyExRate")
  @Column(name = "id_currency_pair")
  Integer idCurrencypair;

  @Schema(description = "Every transaction relating to a security has security account."
      + " A cash transaction may also have a security account in case of security account costs")
  @Column(name = "id_security_account")
  @NotNull(groups = { SecurityTransaction.class })
  private Integer idSecurityaccount;

  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "Used for accrued interest with bonds and daily holding costs with CFD")
  @Column(name = "asset_investment_value_1")
  @Null(groups = { CashTransaction.class })
  private Double assetInvestmentValue1;

  @Schema(description = "Multiplicator for CFD or value per point")
  @Column(name = "asset_investment_value_2")
  @Null(groups = { CashTransaction.class })
  private Double assetInvestmentValue2;

  @Transient
  private Double securityRisk;

  @JsonIgnore
  @Transient
  private Double splitFactorFromBaseTransaction = 1.0;

  public Transaction() {
  }

  public Transaction(Cashaccount cashaccount, Double cashaccountAmount, TransactionType transactionType,
      Date transactionTime) {
    this.cashaccount = cashaccount;
    this.cashaccountAmount = cashaccountAmount;
    this.transactionType = transactionType.getValue();
    this.transactionTime = transactionTime;
  }

  public Transaction(Cashaccount cashAccount, Double cashaccountAmount, TransactionType transactionType,
      Double currencyExRate, Date transactionTime, Integer idSecurityaccount, Double taxCost, String note) {
    this(cashAccount, cashaccountAmount, transactionType, transactionTime);
    this.currencyExRate = currencyExRate;
    this.idSecurityaccount = idSecurityaccount;
    this.taxCost = taxCost;
    this.note = note;
  }

  public Transaction(Integer idSecurityaccount, Cashaccount cashAccount, Security security, Double units,
      Double quotation, TransactionType transactionType, Double taxCost, Double transactionCost, Double accruedInterest,
      Date transactionTime, Double currencyExRate) {
    this(idSecurityaccount, cashAccount, security, null, units, quotation, transactionType, taxCost, transactionCost,
        accruedInterest, transactionTime, currencyExRate, null, null, null);
  }

  public Transaction(Integer idSecurityaccount, Cashaccount cashAccount, Security security, Double cashaccountAmount,
      Double units, Double quotation, TransactionType transactionType, Double taxCost, Double transactionCost,
      Double assetInvestmentValue1, Date transactionTime, Double currencyExRate, Integer idCurrencypair, Date exDate,
      Boolean taxableInterest) {
    this(cashAccount, cashaccountAmount, transactionType, transactionTime);
    this.idSecurityaccount = idSecurityaccount;
    this.units = units;
    this.quotation = quotation;
    this.security = security;
    this.taxCost = taxCost;
    this.transactionCost = transactionCost;
    this.assetInvestmentValue1 = assetInvestmentValue1;
    this.currencyExRate = currencyExRate;
    this.idCurrencypair = idCurrencypair;
    this.exDate = exDate;
    this.taxableInterest = taxableInterest;
  }

  @PrePersist
  public void onPrePersist() {
    transactionDate = DateHelper.getLocalDate(this.transactionTime);
  }

  @PreUpdate
  public void onPreUpdate() {
    transactionDate = DateHelper.getLocalDate(this.transactionTime);
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idTransaction;
  }

  public Integer getIdTransaction() {
    return idTransaction;
  }

  public void setIdTransaction(Integer idTransaction) {
    this.idTransaction = idTransaction;
  }

  public Double getCashaccountAmount() {
    return cashaccountAmount;
  }

  public void setCashaccountAmount(Double cashaccountAmount) {
    this.cashaccountAmount = cashaccountAmount;
  }

  public Integer getConnectedIdTransaction() {
    return connectedIdTransaction;
  }

  public void setConnectedIdTransaction(Integer connectedIdTransaction) {
    this.connectedIdTransaction = connectedIdTransaction;
  }

  public Double getCurrencyExRateNotNull() {
    return (currencyExRate == null || currencyExRate == 0.0) ? 1.0 : currencyExRate;
  }

  public Double getCurrencyExRate() {
    return currencyExRate;
  }

  public void setCurrencyExRate(Double currencyExRate) {
    this.currencyExRate = currencyExRate;
  }

  public Double getUnits() {
    return units;
  }

  public void setUnits(Double units) {
    this.units = units;
  }

  public Double getQuotation() {
    return quotation;
  }

  public void setQuotation(Double quotation) {
    this.quotation = quotation;
  }

  public TransactionType getTransactionType() {
    return TransactionType.getTransactionTypeByValue(this.transactionType);
  }

  public void setTransactionType(TransactionType transactionType) {
    this.transactionType = transactionType.getValue();
  }

  public Boolean isTaxableInterest() {
    return taxableInterest;
  }

  public void setTaxableInterest(Boolean taxableInterest) {
    this.taxableInterest = taxableInterest;
  }

  public Double getTaxCost() {
    return taxCost;
  }

  public void setTaxCost(Double taxCost) {
    this.taxCost = taxCost;
  }

  public Double getTransactionCost() {
    return transactionCost;
  }

  public void setTransactionCost(Double transactionCost) {
    this.transactionCost = transactionCost;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Date getTransactionTime() {
    return transactionTime;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  @JsonIgnore
  public java.sql.Date getTransactionDateAsDate() {
    return idTransaction < 0 ? java.sql.Date.valueOf(DateHelper.getLocalDate(this.transactionTime))
        : java.sql.Date.valueOf(transactionDate);
  }

  public Date getExDate() {
    return exDate;
  }

  public void setExDate(Date exDate) {
    this.exDate = exDate;
  }

  public void setTransactionTime(Date transactionTime) {
    this.transactionTime = transactionTime;
  }

  public Cashaccount getCashaccount() {
    return cashaccount;
  }

  public void setCashaccount(Cashaccount cashAccount) {
    this.cashaccount = cashAccount;
  }

  public Integer getIdCurrencypair() {
    return idCurrencypair;
  }

  public void setIdCurrencypair(Integer idCurrencypair) {
    this.idCurrencypair = idCurrencypair;
  }

  public Integer getIdSecurityaccount() {
    return idSecurityaccount;
  }

  public void setIdSecurityaccount(Integer idSecurityaccount) {
    this.idSecurityaccount = idSecurityaccount;
  }

  public Security getSecurity() {
    return security;
  }

  // TODO why is it call setSecuritycurrency
  public void setSecuritycurrency(Security security) {
    this.security = security;
  }

  public Double getAssetInvestmentValue1() {
    return assetInvestmentValue1;
  }

  public void setAssetInvestmentValue1(Double assetInvestmentValue1) {
    this.assetInvestmentValue1 = assetInvestmentValue1;
  }

  public Double getAssetInvestmentValue2() {
    return assetInvestmentValue2;
  }

  public void setAssetInvestmentValue2(Double assetInvestmentValue2) {
    this.assetInvestmentValue2 = assetInvestmentValue2;
  }

  public Double getSecurityRisk() {
    return securityRisk;
  }

  public void setSecurityRisk(Double securityRisk) {
    this.securityRisk = securityRisk;
  }

  public Double getSplitFactorFromBaseTransaction() {
    return splitFactorFromBaseTransaction;
  }

  public void setSplitFactorFromBaseTransaction(Double splitFactorFromBaseTransaction) {
    this.splitFactorFromBaseTransaction = splitFactorFromBaseTransaction;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  @JsonIgnore
  public Double getInterestExRate(DateTransactionCurrencypairMap dateTransactionCurrencyMap) {
    double exchangeRateToMC = getExchangeRateOnCurrency(dateTransactionCurrencyMap.getMainCurrency(),
        dateTransactionCurrencyMap);
    return this.cashaccountAmount * exchangeRateToMC;
  }

  @JsonIgnore
  public Double getFeeExRate(DateTransactionCurrencypairMap dateTransactionCurrencyMap) {
    double exchangeRateToMC = getExchangeRateOnCurrency(dateTransactionCurrencyMap.getMainCurrency(),
        dateTransactionCurrencyMap);
    return this.cashaccountAmount * exchangeRateToMC * -1.0;
  }

  @JsonIgnore
  public Double getTaxCostExRate(String mainCurency, double exchangeRateToMC) {
    return calcMCValue(mainCurency, this.taxCost, exchangeRateToMC);
  }

  @JsonIgnore
  public double getSeucritiesNetPrice() {
    return quotation * getUnitsMultiplyValuePerPoint();
  }

  public double getSeucritiesNetPrice(double buyQuotation) {
    return units * (quotation - buyQuotation / splitFactorFromBaseTransaction) * getValuePerPoint();
  }

  @JsonIgnore
  public double getUnitsMultiplyValuePerPoint() {
    return units * getValuePerPoint();
  }

  @JsonIgnore
  public double getValuePerPoint() {
    return isMarginInstrumentNotFinanceCost() ? this.assetInvestmentValue2 : 1.0;
  }

  @JsonIgnore
  public boolean isMarginInstrument() {
    return security.isMarginInstrument();
  }

  @JsonIgnore
  public boolean isMarginInstrumentNotFinanceCost() {
    return security.isMarginInstrument() && this.transactionType != TransactionType.FINANCE_COST.getValue();
  }

  @JsonIgnore
  public boolean isMarginOpenPosition() {
    return security.isMarginInstrument() && this.getConnectedIdTransaction() == null;
  }

  @JsonIgnore
  public boolean isMarginClosePosition() {
    return security.isMarginInstrument() && getConnectedIdTransaction() != null
        && this.transactionType != TransactionType.FINANCE_COST.getValue();
  }

  @JsonIgnore
  public Double getTransactionCostCurrencyExRate(String mainCurency, double exchangeRateToMC) {
    return calcMCValue(mainCurency, this.transactionCost, exchangeRateToMC);
  }

  @JsonIgnore
  public double getBasePriceForTransactionCost(String mainCurency, double exchangeRateToMC) {
    double acInterest = this.assetInvestmentValue1 != null ? assetInvestmentValue1 : 0;
    double value = getSeucritiesNetPrice() + acInterest;

    return calcMCValue(mainCurency, value, exchangeRateToMC);
  }

  @JsonIgnore
  public boolean isCashaccountTransfer() {
    return connectedIdTransaction != null && (transactionType == TransactionType.WITHDRAWAL.getValue()
        || transactionType == TransactionType.DEPOSIT.getValue());
  }

  private double getExchangeRateOnCurrency(String mc, DateTransactionCurrencypairMap dateTransactionCurrencyMap) {
    double exchangeRateToMC = 1.0;
    if (!getCashaccount().getCurrency().equals(mc)
        && (getSecurity() == null || !getSecurity().getCurrency().equals(mc))) {

      exchangeRateToMC = dateTransactionCurrencyMap.getPriceByDateAndFromCurrency(
          dateTransactionCurrencyMap.isUseUntilDateForFeeAndInterest() ? dateTransactionCurrencyMap.getUntilDate()
              : getTransactionDateAsDate(),
          getCashaccount().getCurrency(), true);
    }
    return exchangeRateToMC;
  }

  public void clearCurrencypairExRate() {
    if (idCurrencypair == null) {
      currencyExRate = null;

    } else if (currencyExRate == null) {
      idCurrencypair = null;
    }
  }

  /**
   * We do not throw an error if we get data which should not be set because of
   * the transaction. Clear the fields may be better.
   */
  public void clearAccountTransaction() {
    switch (this.getTransactionType()) {
    case FEE:
      connectedIdTransaction = null;
      idCurrencypair = null;
      taxCost = null;
      transactionCost = null;
      break;
    case DEPOSIT:
      idSecurityaccount = null;
      taxCost = null;
      transactionCost = null;
      break;
    case INTEREST_CASHACCOUNT:
      connectedIdTransaction = null;
      idCurrencypair = null;
      idSecurityaccount = null;
      transactionCost = null;
      break;
    default:
      // Withdrawal
      idSecurityaccount = null;
      taxCost = null;
      transactionCost = null;
    }
    this.clearCurrencypairExRate();
  }

  public double calcCostTaxMaybeBasePrice(String mc, SecurityCostPosition securityCostPosition,
      DateTransactionCurrencypairMap dateTransactionCurrencyMap, boolean calcBasePrice) {

    double exchangeRateToMC = getExchangeRateOnCurrency(mc, dateTransactionCurrencyMap);
    double basePriceForTransactionCostMC = 0.0;

    Double taxCostMC = getTaxCostExRate(mc, exchangeRateToMC);

    if (taxCostMC != null) {
      securityCostPosition.taxCostMC = taxCostMC;
    }
    Double transactionCostMC = getTransactionCostCurrencyExRate(mc, exchangeRateToMC);
    securityCostPosition.transactionCostMC = transactionCostMC;
    if (calcBasePrice) {
      basePriceForTransactionCostMC = getBasePriceForTransactionCost(mc, exchangeRateToMC);
    }
    return basePriceForTransactionCostMC;
  }

  /**
   * MC->main currency, T->Transaction</br>
   * When Cashaccount -> CHF (MC), Security -> CHF(MC) then nothing</br>
   * When Cashaccount -> GBP, Security -> CHF (MC) then nothing</br>
   * When Cashaccount -> CHF (MC), Security -> GBP then * exchange Rate (T)</br>
   * When Cashaccount -> GBP, Security -> GBP, MC = CHF then * exchange Rate
   * (GBPCHF(MC)</br>
   * When Cashaccount -> GBP, Security -> USD, MC = CHF then / exchange Rate (T) *
   * exchange Rate (GBPCHF(MC))</br>
   *
   * @param mainCurency
   * @param value
   * @param exchangeRateToMC
   * @return
   */
  private Double calcMCValue(String mainCurency, Double value, double exchangeRateToMC) {
    Double valueMC = null;
    if (value != null) {
      boolean securityIsMC = this.getSecurity().getCurrency().equals(mainCurency);
      boolean cashaccountIsMC = this.getCashaccount().getCurrency().equals(mainCurency);
      boolean cashaccountIsSecurity = getCashaccount().getCurrency().equals(security.getCurrency());
      double exchangeRate = currencyExRate == null || currencyExRate == 0.0 || securityIsMC ? 1.0 : currencyExRate;
      if (!securityIsMC && !cashaccountIsMC && !cashaccountIsSecurity) {
        valueMC = value * exchangeRate * exchangeRateToMC;
      } else {
        if (!securityIsMC && !cashaccountIsMC) {
          valueMC = value * exchangeRateToMC;
        } else {
          valueMC = value * exchangeRate;
        }
      }
    }
    return valueMC;
  }

  @Override
  public int compareTo(Transaction transaction1) {
    return this.transactionTime.compareTo(transaction1.getTransactionTime());
  }

  public void validateCashaccountAmount(Transaction openPositionMarginTransaction, Integer currencyFraction) {
    double calcCashaccountAmount = 0;
    switch (getTransactionType()) {
    case ACCUMULATE:
    case REDUCE:
    case DIVIDEND:
    case FINANCE_COST:
      checkNegativeQuoataion();
      if (this.security.isMarginInstrument()) {
        calcCashaccountAmount = validateSecurityMarginCashaccountAmount(openPositionMarginTransaction);
      } else {
        calcCashaccountAmount = validateSecurityGeneralCashaccountAmount(0);
      }
      break;
    default:
      calcCashaccountAmount = cashaccountAmount;
    }
    calcCashaccountAmount = DataHelper.round(calcCashaccountAmount, currencyFraction);
    double roundCashaccountAmount = DataHelper.round(cashaccountAmount, currencyFraction);

    if (roundCashaccountAmount == calcCashaccountAmount) {
      if (quotation != null && GlobalConstants.AUTO_CORRECT_TO_AMOUNT) {
        if (calcCashaccountAmount != DataHelper.round(cashaccountAmount, currencyFraction + 1)) {
          correctSecurityTransactionToAmount(cashaccountAmount - roundCashaccountAmount);
        }
      }
    } else {
      throw new DataViolationException("cashaccount.amount", "gt.cashaccount.amount.calc",
          new Object[] { calcCashaccountAmount, roundCashaccountAmount, cashaccountAmount });
    }
  }

  private void checkNegativeQuoataion() {
    if (getTransactionType() != TransactionType.DIVIDEND && getTransactionType() != TransactionType.FINANCE_COST
        && quotation < 0.0) {
      throw new IllegalArgumentException("Only dividend allows negative value for quotation!");
    }
  }

  private double validateSecurityMarginCashaccountAmount(Transaction openPositionMarginTransaction) {
    double taxCostC = taxCost == null ? 0.0 : taxCost;
    double transactionCostC = transactionCost == null ? 0 : transactionCost;
    if (getConnectedIdTransaction() == null) {
      // Open new position accumulate or reduce
      double securityRiskC = DataHelper.round(Math.abs(validateSecurityGeneralCashaccountAmount(0))
          * (getTransactionType() == TransactionType.ACCUMULATE ? 1.0 : -1.0));
      securityRisk = DataHelper.round(securityRisk);

      if (securityRiskC != securityRisk) {
        throw new DataViolationException("security.risk", "gt.security.risk.calc",
            new Object[] { securityRiskC, securityRisk });
      }

      // cash account amount is tax and transaction cost
      return DataHelper.divideMultiplyExchangeRate((taxCostC + transactionCostC) * -1.0, currencyExRate,
          cashaccount.getCurrency(), security.getCurrency());

    } else if (getTransactionType() == TransactionType.FINANCE_COST) {
      return validateSecurityGeneralCashaccountAmount(0) * -1;
    } else {
      // Close a open position or finance cost
      return validateSecurityGeneralCashaccountAmount(openPositionMarginTransaction.getQuotation());
    }
  }

  private void correctSecurityTransactionToAmount(double diff) {
    if (idCurrencypair != null) {
      double oldCurrencyExRate = currencyExRate;
      currencyExRate = DataHelper.round((validateSecurityGeneralCashaccountAmount(0) - diff)
          / calculateSecurityTransactionAmountWithoutExchangeRate(0), GlobalConstants.FID_MAX_FRACTION_DIGITS);
      log.debug("Corrected currency exchange rate for difference {} from {} to {}", diff, oldCurrencyExRate,
          currencyExRate);

    } else if (quotation != null) {
      double oldQuotation = quotation;
      quotation = DataHelper.round((getSeucritiesNetPrice(0) + diff) / this.units,
          GlobalConstants.FID_MAX_FRACTION_DIGITS);
      log.debug("Corrected quotation for difference {} from {} to {}", diff, oldQuotation, quotation);
      cashaccountAmount = this.validateSecurityGeneralCashaccountAmount(0);
    }
  }

  // It is public for test
  public double validateSecurityGeneralCashaccountAmount(double buyQuotation) {
    return DataHelper.divideMultiplyExchangeRate(calculateSecurityTransactionAmountWithoutExchangeRate(buyQuotation),
        currencyExRate, security.getCurrency(), cashaccount.getCurrency());
  }

  private double calculateSecurityTransactionAmountWithoutExchangeRate(double buyQuotation) {
    double calcCashaccountAmount = getSeucritiesNetPrice(buyQuotation);
    if (getTransactionType() == TransactionType.ACCUMULATE) {
      calcCashaccountAmount = (calcCashaccountAmount + calculateOtherCosts()) * -1.0;
    } else {
      // For reduce and dividend
      calcCashaccountAmount -= calculateOtherCosts();
    }
    return calcCashaccountAmount;
  }

  private double calculateOtherCosts() {
    double taxCostC = taxCost == null ? 0.0 : taxCost;
    double transactionCostC = transactionCost == null ? 0 : transactionCost;
    double accruedInterestC = assetInvestmentValue1 != null && !isMarginInstrument() ? assetInvestmentValue1 : 0;
    return taxCostC + transactionCostC
        + accruedInterestC * (getTransactionType() == TransactionType.ACCUMULATE ? 1.0 : -1.0);
  }

  @Override
  public String toString() {
    return "Transaction [idTransaction=" + idTransaction + ", units=" + units + ", quotation=" + quotation
        + ", transactionType=" + transactionType + ", connectedIdTransaction=" + connectedIdTransaction
        + ", taxableInterest=" + taxableInterest + ", taxCost=" + taxCost + ", transactionCost=" + transactionCost
        + ", note=" + note + ", cashaccountAmount=" + cashaccountAmount + ", transactionTime=" + transactionTime
        + ", transactionDate=" + transactionDate + ", exDate=" + exDate + ", security=" + security + ", cashaccount="
        + cashaccount + ", currencyExRate=" + currencyExRate + ", idCurrencypair=" + idCurrencypair
        + ", idSecurityaccount=" + idSecurityaccount + ", idTenant=" + idTenant + ", assetInvestmentValue1="
        + assetInvestmentValue1 + ", assetInvestmentValue2=" + assetInvestmentValue2 + ", securityRisk=" + securityRisk
        + "]";
  }

  public interface CashTransaction extends Default {
  }

  public interface SecurityTransaction extends Default {
  }

}
