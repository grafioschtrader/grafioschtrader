package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.TransactionsMarginOpenUnits;
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
    this(mainCurrency, currencyPrecisionMap.getOrDefault(mainCurrency, BaseConstants.FID_STANDARD_FRACTION_DIGITS));
    this.securitycurrency = security;
    this.precision = currencyPrecisionMap.getOrDefault(security.getCurrency(),
        BaseConstants.FID_STANDARD_FRACTION_DIGITS);
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

  
}
