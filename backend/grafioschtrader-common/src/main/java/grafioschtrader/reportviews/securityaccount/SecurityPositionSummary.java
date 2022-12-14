package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One for every single security. It contain also data of a single transaction,
 * those data are probably transfered to other instance, for example when used
 * for security transaction report.
 *
 */
public class SecurityPositionSummary extends SecuritycurrencyPositionSummary<Security> {

  public final String mainCurrency;

  /**
   * Historical rates in the past must be adjusted according to the intervening
   * splits.
   */
  public double closePriceFactor = 1.0;

  /**
   * units after transaction
   */
  public double units;

  public double splitFactorFromBaseTransaction = 1.0;

  /**
   * Some portfolios may contain more than only one security account. In this case
   * a cash account can be connected to a certain security account.
   */
  public Integer usedIdSecurityaccount;

  /**
   * Cost for Transaction
   */
  public double transactionCost;
  public double transactionCostMC;

  /**
   * Cost for Tax
   */
  public double taxCost;
  public double taxCostMC;

  @Schema(description = "Gain or loss in the currency of the security over all transaction.")
  public double gainLossSecurity;

  @Schema(description = "Amount of gain or loss in the main currency.")
  public double gainLossSecurityMC;

  @Schema(description = "The value of a security need not be equal to the risk of the security if it is leveraged or inverse.")
  public double securityRiskMC;

  @Schema(description = "Value of this position. It is also set when there is no relevance to the cash account balance.")
  public double valueSecurity;
  public double valueSecurityMC;

  /**
   * The same as valueSecurity when not a margin product. It is relevant for
   * account.
   */
  public double accountValueSecurity;
  public double accountValueSecurityMC;

  public double currencyGainLossMC;

  /////////////////////////////////////////////////////////////
  // The following members are only for internal use
  ////////////////////////////////////////////////////////////
  @JsonIgnore
  public double openUnitsTimeValuePerPoint;

  @JsonIgnore
  public double adjustedCostBase;

  @JsonIgnore
  public double adjustedCostBaseMC;

  @JsonIgnore
  public double balanceSecurityCurrency;

  /**
   * For a Watchlist the open positions must be recalculated.
   */
  @JsonIgnore
  public boolean reCalculateOpenPosition;

  /////////////////////////////////////////////////////////////
  // The following members are only for a single transaction
  // and are used for calculation purpose
  ////////////////////////////////////////////////////////////

  /**
   * Gain or loss in the currency of the security
   */
  @JsonIgnore
  public Double transactionGainLoss;

  @JsonIgnore
  public Double transactionGainLossPercentage;

  @JsonIgnore
  public Double transactionGainLossMC;

  @JsonIgnore
  public Double transactionExchangeRate;

  /**
   * Contains open margin position with transaction id as key
   * TransactionsMarginOpenUnits.
   */
  @JsonIgnore
  private Map<Integer, TransactionsMarginOpenUnits> transactionsMarginOpenUnitsMap;

  /**
   * How match was gain/loss on main currency on this transaction
   */
  @JsonIgnore
  public Double transactionCurrencyGainLossMC;

  @JsonIgnore
  public int precision;
  @JsonIgnore
  public int precisionMC;

  public SecurityPositionSummary(String mainCurrency, int precisionMC) {
    this.mainCurrency = mainCurrency;
    this.precisionMC = precisionMC;
  }

  public SecurityPositionSummary(String mainCurrency, Security security, Map<String, Integer> currencyPrecisionMap,
      Integer usedIdSecurityaccount) {
    this(mainCurrency, security, currencyPrecisionMap);
    this.usedIdSecurityaccount = usedIdSecurityaccount;
  }

  public SecurityPositionSummary(String mainCurrency, Security security, Map<String, Integer> currencyPrecisionMap) {
    this(mainCurrency, currencyPrecisionMap.getOrDefault(mainCurrency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS));
    this.securitycurrency = security;
    this.precision = currencyPrecisionMap.getOrDefault(security.getCurrency(),
        GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  public void roundUnits() {
    units = DataHelper.round(units);
  }
  
  public double getUnits() {
    return DataHelper.round(units);
  }

  public double getAccountValueSecurity() {
    return DataHelper.round(accountValueSecurity, precision);
  }

  public double getAccountValueSecurityMC() {
    return DataHelper.round(accountValueSecurityMC, precisionMC);
  }

  public double getGainLossSecurityMC() {
    return DataHelper.round(gainLossSecurityMC, precisionMC);
  }

  public void setGainLossSecurityMC(double gainLossSecurityMC) {
    this.gainLossSecurityMC = gainLossSecurityMC;
  }

  public double getTransactionCost() {
    return DataHelper.round(transactionCost, precision);
  }

  public double getTransactionCostMC() {
    return DataHelper.round(transactionCostMC, precisionMC);
  }

  public double getTaxCost() {
    return DataHelper.round(taxCost, precision);
  }

  public double getTaxCostMC() {
    return DataHelper.round(taxCostMC, precisionMC);
  }

  public double getGainLossSecurity() {
    return DataHelper.round(gainLossSecurity, precision);
  }

  public double getValueSecurity() {
    return DataHelper.round(valueSecurity, precision);
  }

  public double getValueSecurityMC() {
    return DataHelper.round(valueSecurityMC, precisionMC);
  }
    

  public double getSecurityRiskMC() {
    return DataHelper.round(securityRiskMC, precisionMC);
  }

  public double getCurrencyGainLossMC() {
    return DataHelper.round(currencyGainLossMC, precisionMC);
  }

  public void resetForOpenMargin() {
    gainLossSecurity = 0.0;
    adjustedCostBase = 0.0;
    units = 0.0;
  }

  public void calcGainLossByPrice(final Double price) {
    valueSecurity = price * (openUnitsTimeValuePerPoint == 0 ? units : openUnitsTimeValuePerPoint);
    if (this.securitycurrency.isMarginInstrument()) {
      calcGainLossByPriceForMargin(price);
    } else {
      gainLossSecurity = gainLossSecurity + valueSecurity - units * adjustedCostBase / units;
    }
    transactionGainLossPercentage = DataHelper.roundStandard(gainLossSecurity * 100 / adjustedCostBase);
  }

  private void calcGainLossByPriceForMargin(final Double price) {
    double openWinLose = 0d;
    for (TransactionsMarginOpenUnits tmou : transactionsMarginOpenUnitsMap.values()) {
      // TODO fix transaction cost
      if (!tmou.markForRemove) {
        openWinLose += tmou.calcGainLossOnPositionClose(price, 0);
      }
    }

    gainLossSecurity = DataHelper.roundStandard(gainLossSecurity + openWinLose);
    accountValueSecurity += DataHelper.roundStandard(openWinLose);
  }

  /**
   *
   * @param currencyExchangeRate Normally the latest currency exchange rate to
   *                             calculate some fields to the main currency
   */
  public void calcMainCurrency(double currencyExchangeRate) {
    gainLossSecurityMC = gainLossSecurity * currencyExchangeRate;
    valueSecurityMC = valueSecurity * currencyExchangeRate;
    if (securitycurrency.isMarginInstrument()) {
      accountValueSecurityMC = accountValueSecurity * currencyExchangeRate;
    } else {
      accountValueSecurity = valueSecurity;
      accountValueSecurityMC = valueSecurityMC;

    }
    transactionCostMC = transactionCost * currencyExchangeRate;
    taxCostMC = taxCost * currencyExchangeRate;

    if (securitycurrency.getId() < 0) {
      // It is may be a cash account and not a real security
      valueSecurity = 0.0;
      valueSecurityMC = 0.0;
    }
  }

  @JsonIgnore
  public double getPositionGainLossPercentage() {
    return DataHelper
        .roundStandard(securitycurrency.isMarginInstrument() ? (this.gainLossSecurity / this.adjustedCostBase * 100)
            : transactionGainLossPercentage);
  }

  public Security getSecurity() {
    return securitycurrency;
  }

  @JsonIgnore
  public Map<Integer, TransactionsMarginOpenUnits> getTransactionsMarginOpenUnitsMap() {
    if (transactionsMarginOpenUnitsMap == null) {
      transactionsMarginOpenUnitsMap = new HashMap<>();
    }
    return transactionsMarginOpenUnitsMap;
  }

  @JsonIgnore
  public List<TransactionsMarginOpenUnits> getTransactionsMarginOpenUnits() {
    List<TransactionsMarginOpenUnits> transactionsMarginOpenUnitsList = new ArrayList<>(
        transactionsMarginOpenUnitsMap.values());
    Collections.sort(transactionsMarginOpenUnitsList);
    return transactionsMarginOpenUnitsList;
  }

  public void removeClosedMarginPosition() {
    if (transactionsMarginOpenUnitsMap != null) {
      transactionsMarginOpenUnitsMap.entrySet().removeIf(tmou -> tmou.getValue().markForRemove);
    }
  }

  @Override
  public String toString() {
    return "SecurityPositionSummary [mainCurrency=" + mainCurrency + ", units=" + units + ", adjustedCostBase="
        + adjustedCostBase + ", usedIdSecurityaccount=" + usedIdSecurityaccount + ", transactionCost=" + transactionCost
        + ", transactionCostMC=" + transactionCostMC + ", taxCost=" + taxCost + ", taxCostMC=" + taxCostMC
        + ", gainLossSecurity=" + gainLossSecurity + ", gainLossSecurityMC=" + gainLossSecurityMC + ", valueSecurity="
        + valueSecurity + ", valueSecurityMC=" + valueSecurityMC + ", openUnitsTimeValuePerPoint="
        + openUnitsTimeValuePerPoint + ", adjustedCostBaseMC=" + adjustedCostBaseMC + ", balanceSecurityCurrency="
        + balanceSecurityCurrency + ", transactionGainLoss=" + transactionGainLoss + ", transactionGainLossPercentage="
        + transactionGainLossPercentage + ", transactionGainLossMC=" + transactionGainLossMC
        + ", transactionExchangeRate=" + transactionExchangeRate + ", transactionsMarginOpenUnitsMap="
        + transactionsMarginOpenUnitsMap + ", transactionCurrencyGainLossMC=" + transactionCurrencyGainLossMC + "]";
  }

  /**
   * It holds the calculation for a single open margin transaction with its close
   * transactions for a single security. Since a close position has a relation
   * with open position, this construct is needed.
   *
   */
  public static class TransactionsMarginOpenUnits implements Comparable<TransactionsMarginOpenUnits> {
    /**
     * Transaction which opens the position
     */
    public Transaction openTransaction;
    /**
     * Transaction can be closed partly
     */
    public double openUnits;

    public double realUntisCounter;

    /**
     * Always 0 or positive
     */
    public double openPartPrice;

    /**
     * Adjusted cost base for a open position
     */
    public double expenseIncome;

    public boolean markForRemove;

    /**
     * Split factor from open margin transaction to hypothetical close transaction
     */
    private final double splitFactorFromBaseTransaction;

    private double splitFactorSinceOpen = 1.0;

    private double shortFactor = 1.0;

    /**
     *
     *
     * @param openTransaction Transaction which opened a position
     * @param openUnits
     */
    public TransactionsMarginOpenUnits(Transaction openTransaction, double openUnits, double expenseIncome,
        double splitFactorFromBaseTransaction) {
      this.openTransaction = openTransaction;
      this.openUnits = openUnits;
      this.expenseIncome = expenseIncome;
      this.splitFactorFromBaseTransaction = splitFactorFromBaseTransaction;
      this.shortFactor = openTransaction.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1;
      this.openPartPrice = openTransaction.getSeucritiesNetPrice();
      this.realUntisCounter = openTransaction.getUnits() * shortFactor;
    }

    @Override
    public int compareTo(TransactionsMarginOpenUnits transactionsMarginOpenUnits1) {
      return openTransaction.getTransactionTime()
          .compareTo(transactionsMarginOpenUnits1.openTransaction.getTransactionTime());
    }

    private double calcGainLossOnPositionClose(double price, double transactionCost) {
      return this.calcGainLossOnClosePosition(price, transactionCost, Math.abs(this.openUnits),
          splitFactorFromBaseTransaction);
    }

    /**
     * Calculate gain or loss over a single open position
     *
     * @param price
     * @param transactionCost
     * @param unitsSplited      A positiv number is expected
     * @param splitFactorToOpen
     * @return
     */
    public double calcGainLossOnClosePosition(double price, double transactionCost, double unitsSplited,
        double splitFactorToOpen) {
      if (splitFactorToOpen != 1.0 && splitFactorToOpen != splitFactorSinceOpen) {
        realUntisCounter = realUntisCounter * splitFactorToOpen / splitFactorSinceOpen;
        splitFactorSinceOpen = splitFactorToOpen;
      }

      double openPartPositionPrice = openPartPrice / realUntisCounter * Math.abs(unitsSplited);
      openPartPrice += openPartPositionPrice;
      realUntisCounter += unitsSplited;
      return price * unitsSplited * shortFactor * openTransaction.getValuePerPoint() - transactionCost
          - openPartPositionPrice;
    }

    @Override
    public String toString() {
      return "TransactionsMarginOpenUnits [openTransaction=" + openTransaction + ", openUnits=" + openUnits
          + ", adjustedCostBase=" + expenseIncome + ", markForRemove=" + markForRemove
          + ", splitFactorFromBaseTransaction=" + splitFactorFromBaseTransaction + "]";
    }

  }

}
