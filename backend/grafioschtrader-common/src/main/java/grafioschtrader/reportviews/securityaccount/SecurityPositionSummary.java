package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.DataHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One for every single security. It contain also data of a single transaction, those data are probably transfered to
 * other instance, for example when used for security transaction report.
 *
 */
public class SecurityPositionSummary extends SecuritycurrencyPositionSummary<Security> {
  
  @Schema(description = "Main portfolio currency for multi-currency normalization")
  public final String mainCurrency;

  @Schema(description = "Price adjustment factor for stock splits and corporate actions")
  public double closePriceFactor = 1.0;

  @Schema(description = "Current number of units held in the position")
  public double units;

  @Schema(description = "Cumulative split factor from base transaction for corporate action adjustments")
  public double splitFactorFromBaseTransaction = 1.0;

  @Schema(description = "Security account identifier for multi-account portfolio tracking")
  public Integer usedIdSecurityaccount;

  @Schema(description = "Total transaction costs in security currency")
  public double transactionCost;
  @Schema(description = "Total transaction costs in main currency")
  public double transactionCostMC;

  @Schema(description = "Tax-related costs in security currency")
  public double taxCost;
  @Schema(description = "Tax-related costs in main currency")
  public double taxCostMC;

  @Schema(description = "Gain or loss in the currency of the security over all transaction.")
  public double gainLossSecurity;

  @Schema(description = "Amount of gain or loss in the main currency.")
  public double gainLossSecurityMC;

  @Schema(description = "The value of a security need not be equal to the risk of the security if it is leveraged or inverse.")
  public double securityRiskMC;

  @Schema(description = "Value of this position. It is also set when there is no relevance to the cash account balance.")
  public double valueSecurity;
  @Schema(description = "Current market value in main currency")
  public double valueSecurityMC;

  @Schema(description = "Account value equal to market value for standard instruments, differs for margin products")
  public double accountValueSecurity;
  @Schema(description = "Account value in main currency")
  public double accountValueSecurityMC;

  @Schema(description = "Currency exchange gains/losses in main currency")
  public double currencyGainLossMC;

  /////////////////////////////////////////////////////////////
  // The following members are only for internal use
  ////////////////////////////////////////////////////////////
  /** Internal calculation field for margin instrument valuation */
  @JsonIgnore
  public double openUnitsTimeValuePerPoint;

  /** Adjusted cost basis in security currency for internal calculations */
  @JsonIgnore
  public double adjustedCostBase;

  /** Adjusted cost basis in main currency for internal calculations */
  @JsonIgnore
  public double adjustedCostBaseMC;

  /** Security currency balance for internal tracking */
  @JsonIgnore
  public double balanceSecurityCurrency;

  /**
   * Flag indicating whether open positions need recalculation, typically used in watchlist scenarios where position
   * data may become stale and require refresh from current market data and transaction history.
   */
  @JsonIgnore
  public boolean reCalculateOpenPosition;

  /////////////////////////////////////////////////////////////
  // The following members are only for a single transaction
  // and are used for calculation purpose
  ////////////////////////////////////////////////////////////

  /**
   * Gain or loss for a specific transaction in the security's native currency. Used during transaction processing and
   * detailed transaction analysis to track individual trade performance within the overall position.
   */
  @JsonIgnore
  public Double transactionGainLoss;

  @JsonIgnore
  public Double transactionGainLossPercentage;

  @JsonIgnore
  public Double transactionGainLossMC;

  @JsonIgnore
  public Double transactionExchangeRate;

  /** Contains open margin position with transaction id as key TransactionsMarginOpenUnits. */
  @JsonIgnore
  private Map<Integer, TransactionsMarginOpenUnits> transactionsMarginOpenUnitsMap;

  /** How match was gain/loss on main currency on this transaction */
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
    units = DataBusinessHelper.round(units);
  }

  public double getUnits() {
    return DataBusinessHelper.round(units);
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

  /**
   * Resets position metrics for open margin calculation scenarios. Used when recalculating margin positions from
   * transaction history, ensuring clean starting state for accurate position reconstruction.
   */
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
    transactionGainLossPercentage = DataBusinessHelper.roundStandard(gainLossSecurity * 100 / adjustedCostBase);
  }

  /**
   * Specialized gain/loss calculation for margin instruments with position tracking. Handles individual open margin
   * positions that may have different entry prices and leverage factors, providing accurate performance measurement for
   * sophisticated trading strategies involving partial position closures and variable leverage.
   *
   * @param price current market price for margin position valuation
   */
  private void calcGainLossByPriceForMargin(final Double price) {
    double openWinLose = 0d;
    for (TransactionsMarginOpenUnits tmou : transactionsMarginOpenUnitsMap.values()) {
      // TODO fix transaction cost
      if (!tmou.markForRemove) {
        openWinLose += tmou.calcGainLossOnPositionClose(price, 0);
      }
    }
    gainLossSecurity = DataBusinessHelper.roundStandard(gainLossSecurity + openWinLose);
    accountValueSecurity += DataBusinessHelper.roundStandard(openWinLose);
  }

  /**
   * Converts security currency values to main currency using the specified exchange rate.
   *
   * @param currencyExchangeRate exchange rate from security currency to main currency
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

  /**
   * Calculates percentage gain/loss for the entire position with margin instrument handling. Provides relative
   * performance measurement that accounts for the different calculation methodologies required for standard instruments
   * versus margin positions.
   *
   * @return percentage gain/loss for the position
   */
  @JsonIgnore
  public double getPositionGainLossPercentage() {
    return DataBusinessHelper
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
   * Represents an individual open margin transaction with its associated close transactions for sophisticated margin
   * position tracking. Maintains the relationship between opening and closing trades for accurate gain/loss calculation
   * in trading scenarios involving partial position closures and variable leverage.
   * 
   * This inner class handles the intricate calculations required for margin trading where positions can be opened and
   * closed in multiple transactions, each with potentially different prices, costs, and leverage factors. It maintains
   * the necessary state to accurately attribute gains and losses to specific opening transactions when positions are
   * closed partially or completely.
   * 
   */
  public static class TransactionsMarginOpenUnits implements Comparable<TransactionsMarginOpenUnits> {

    /** Original transaction that opened this margin position */
    public Transaction openTransaction;

    /** Remaining open units after partial closures */
    public double openUnits;

    /**
     * Counter for real units adjusted for corporate actions and position changes. Maintains accurate unit tracking
     * across stock splits events that affect the quantity of securities in the open position.
     */
    public double realUntisCounter;

    /**
     * Price per unit for the open portion of the position, always zero or positive. Represents the effective entry
     * price for this specific open position component, used as the reference point for gain/loss calculations when the
     * position is closed.
     */
    public double openPartPrice;

    /**
     * Total expenses and income associated with opening this margin position. Includes transaction costs, fees, and any
     * income received, forming part of the adjusted cost basis for accurate performance measurement.
     */
    public double expenseIncome;

    /**
     * Flag indicating whether this open position should be removed from tracking. Set to true when the position has
     * been fully closed and all associated calculations have been completed for final cleanup operations.
     */
    public boolean markForRemove;

    /**
     * Split factor adjustment from the original base transaction to this open position. Ensures consistent unit and
     * price calculations across corporate actions that occurred between the base transaction date and the opening of
     * this position.
     */
    private final double splitFactorFromBaseTransaction;

    /**
     * Cumulative split factor since this position was opened, tracking all corporate actions that have affected the
     * position since its opening date. Used to maintain accurate unit calculations and price adjustments for ongoing
     * position management.
     */
    private double splitFactorSinceOpen = 1.0;

    /**
     * Direction multiplier for the position: +1 for long (accumulate) positions, -1 for short (reduce) positions.
     * Ensures correct gain/loss calculation direction based on whether the position benefits from price increases or
     * decreases.
     */
    private double shortFactor = 1.0;

    /**
     * Creates a new open margin position tracker for sophisticated position management. Initializes all necessary
     * parameters for accurate gain/loss calculation across the life of the margin position, including corporate action
     * adjustments and directional position handling.
     *
     * @param openTransaction                the transaction that opened this margin position
     * @param openUnits                      number of units in the opening position
     * @param expenseIncome                  total expenses and income for the opening transaction
     * @param splitFactorFromBaseTransaction split factor for corporate action consistency
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

    /**
     * Calculates gain/loss for closing this entire open position at the specified price. Convenience method that closes
     * the full remaining open position using the current split factor context and zero transaction costs for
     * mark-to-market calculations.
     *
     * @param price           closing price for gain/loss calculation
     * @param transactionCost transaction costs for the closing (currently not implemented)
     * @return gain or loss from closing the position at the specified price
     */
    private double calcGainLossOnPositionClose(double price, double transactionCost) {
      return this.calcGainLossOnClosePosition(price, transactionCost, Math.abs(this.openUnits),
          splitFactorFromBaseTransaction);
    }

    /**
     * Calculates gain or loss for closing a specified quantity of this open position. Performs sophisticated
     * calculations that account for corporate actions, leverage factors, and partial position closures to provide
     * accurate performance attribution for margin trading scenarios.
     * 
     * @param price             closing price for the position portion
     * @param transactionCost   transaction costs associated with the closure
     * @param unitsSplited      number of units being closed (split-adjusted)
     * @param splitFactorToOpen split factor from current date back to opening
     * @return gain or loss from closing the specified position portion
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
