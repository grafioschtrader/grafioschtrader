package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.TransactionsMarginOpenUnits;
import grafioschtrader.types.AlgoRecommendationAction;
import grafioschtrader.types.CostBasisMethod;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One for every single security. It contain also data of a single transaction, those data are probably transfered to
 * other instance, for example when used for security transaction report.
 *
 */
public class SecurityPositionSummary extends SecuritycurrencyPositionSummary<Security> {

  @Schema(description = "Strategy security added for comparison only, with no portfolio position in this report")
  public boolean comparisonOnly;

  /**
   * Target share of total net equity in percentage points, from the AlgoTop selected on the report. Null on every
   * report that is not a rebalancing comparison, which is what keeps its columns out of the other groupings.
   */
  @Schema(description = "Target share of total net equity in percentage points; only set on the rebalancing report")
  public Double targetPercentage;
  public Double parentDeviation;
  public Double securityDeviationPercentage;

  @Schema(description = "Actual share of total net equity in percentage points, measured as gross exposure")
  public Double actualPercentage;

  @Schema(description = "Actual minus target, in percentage points")
  public Double deviationPercentage;

  @Schema(description = "Direction of the proposed trade, not of the exposure")
  public AlgoRecommendationAction recommendedAction;

  @Schema(description = "Size of the proposed trade in main currency")
  public Double recommendedAmount;

  @Schema(description = "Size of the proposed trade in units; null when the instrument cannot be priced")
  public Double recommendedUnits;

  @Schema(description = "Locale independent reason token explaining the recommended action")
  public String recommendationReason;

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

  @Schema(description = """
      The part of gainLossSecurity that came in as income: dividends and accrued interest, gross or net of tax as the
      tenant has chosen. Zero for a margin instrument.""")
  public double incomeGainLossSecurity;
  @Schema(description = "incomeGainLossSecurity at the exchange rate of the report")
  public double incomeGainLossSecurityMC;

  @Schema(description = """
      The part of gainLossSecurity that is not realized: the value of the open position less its remaining cost, for a
      margin instrument the result of the open positions. Zero for a closed position. The remaining cost depends on
      the cost basis method, so this part and the realized one shift against each other when the method changes.""")
  public double unrealizedGainLossSecurity;
  @Schema(description = "unrealizedGainLossSecurity at the exchange rate of the report")
  public double unrealizedGainLossSecurityMC;

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

  @Schema(description = """
      Share of this position in the report total, in percentage points: accountValueSecurityMC divided by
      grandAccountValueSecurityMC. Negative for a position with a negative account value, such as a CFD or Forex
      position at a loss or an overdrawn cash account. Null when the report total is not positive.""")
  public Double shareOfTotalPercentage;

  @Schema(description = """
      Gain or loss in the main currency caused purely by exchange rate movement, measured over the whole position:
      the net amount still invested in the security currency valued at the reporting date rate, less the same flows
      valued at the rates that applied when they happened. Purchases, sales, dividends and accrued interest all
      count. Together with gainLossSecurityMC it adds up to the complete main currency result of the position.""")
  public double gainLossCurrencyMC;

  @Schema(description = """
      The cost basis methods of the security accounts whose purchases and sales went into this position. More than one
      when the security is held in portfolios with different methods. Empty for a margin instrument, which has no
      method.""")
  public Set<CostBasisMethod> costBasisMethods = EnumSet.noneOf(CostBasisMethod.class);

  /////////////////////////////////////////////////////////////
  // Disposal cost estimate of the hypothetical sale. Every member stays null unless the global parameter
  // gt.disposal.cost.estimate is switched on, so the JSON of the reports is unchanged otherwise.
  /////////////////////////////////////////////////////////////

  @Schema(description = """
      Estimated commission of selling the whole position at the report date, in security currency. Null when it was
      not estimated or when no security account could be priced.""")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Double disposalTransactionCost;

  @Schema(description = "Estimated transaction tax of selling the whole position, in security currency")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Double disposalTaxCost;

  @Schema(description = """
      Estimated markup of converting the proceeds into the currency of the settlement cash account, in security
      currency. Zero when the security settles in its own currency.""")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Double disposalFxCost;

  @Schema(description = "Sum of the known disposal cost components in main currency")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Double disposalCostMC;

  @Schema(description = "Account value in main currency less the known disposal costs")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Double valueAfterDisposalMC;

  @Schema(description = """
      False when at least one disposal cost component could not be estimated, for example because a fee model, a
      tax model or a matching rule is missing. Null when the position was not estimated at all, which is the case for
      closed positions, margin instruments and cash accounts.""")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Boolean disposalComplete;

  @Schema(description = "Matched rules and reasons of unknown components, per security account")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<DisposalCostDetail> disposalDetails;

  /**
   * Net proceeds in security currency of the units that settle into a cash account in the currency of the security. The
   * portfolio report converts them into the main currency together with the balance of that cash account.
   */
  @JsonIgnore
  public double disposalSameCurrencyNet;

  /////////////////////////////////////////////////////////////
  // The following members are only for internal use
  ////////////////////////////////////////////////////////////
  /** Internal calculation field for margin instrument valuation */
  @JsonIgnore
  public double openUnitsTimeValuePerPoint;

  /** Adjusted cost basis in security currency for internal calculations */
  @JsonIgnore
  public double adjustedCostBase;

  /**
   * Net amount still invested in the security currency: purchase costs less sale proceeds, dividends and accrued
   * interest. Counterpart of {@code balanceCurrencyTransaction} on the cash account side, and the first half of the
   * currency attribution computed in {@link #calcMainCurrency(double)}.
   */
  @JsonIgnore
  public double netInvestedSC;

  /**
   * The same flows as {@link #netInvestedSC}, each valued at the exchange rate that applied on its own transaction
   * date. The difference between the two, once the first is revalued at the reporting date rate, is the currency
   * result. Flows of a security already denominated in the main currency are left out of both, which is the same as
   * booking them at a rate of one.
   */
  @JsonIgnore
  public double netInvestedMC;

  /**
   * Sum of the flows of all transactions in security currency, positive into the position: purchases with their cost,
   * less sale proceeds after cost, income and, for a margin instrument, the settled results. Unlike
   * {@link #netInvestedSC} it is kept for every currency. Hypothetical transactions add nothing, so for a non-margin
   * security valued with a hypothetical sale without estimated cost {@code gainLossSecurity = units * price -
   * positionFlow}. The period performance per security reads the flow of a transaction as the change it causes here.
   */
  @JsonIgnore
  public double positionFlow;

  /** Excluded dividend tax in security currency (when excludeDivTaxcost is enabled) */
  @JsonIgnore
  public double excludedDivTax;

  /** Excluded dividend tax converted to main currency */
  @JsonIgnore
  public double excludedDivTaxMC;

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

  /**
   * Average cost basis in security currency allocated to the units of a sale, the amount {@link #transactionGainLoss}
   * of that sale is measured against. Kept because the sale removes it from {@link #adjustedCostBase}, so after a
   * hypothetical sale of the whole position the cost basis of the open units is only available here.
   */
  @JsonIgnore
  public Double transactionCostBase;

  @JsonIgnore
  public Double transactionExchangeRate;

  /**
   * Unrealized gain/loss of the open margin positions in security currency, keyed by the currency of the cash account
   * each position settles into. Closing a position books its gain/loss to the cash account of the opening transaction,
   * which is not necessarily in the currency of the security (a CHF/JPY Forex position is priced in JPY and may settle
   * into a CHF account). Only set for margin instruments.
   */
  @JsonIgnore
  public Map<String, Double> marginGainLossBySettlementCurrency;

  /**
   * {@link #marginGainLossBySettlementCurrency} converted to main currency with the rate of the security currency, so
   * its values add up to {@link #accountValueSecurityMC} up to rounding. Only set for margin instruments.
   */
  @JsonIgnore
  public Map<String, Double> marginGainLossBySettlementCurrencyMC;

  /** Contains open margin position with transaction id as key TransactionsMarginOpenUnits. */
  @JsonIgnore
  private Map<Integer, TransactionsMarginOpenUnits> transactionsMarginOpenUnitsMap;

  /**
   * Signed flow of this transaction in the security currency, positive when money went into the position. Kept so the
   * per-transaction currency result can be finished once the reporting date rate is known, which is not the case yet
   * while the transactions are being walked.
   */
  @JsonIgnore
  public Double transactionFlowSC;

  /** Method by which the sale of this row was calculated, null for every other row and for the hypothetical sale. */
  @JsonIgnore
  public CostBasisMethod transactionCostBasisMethod;

  /** The lots a FIFO or LIFO sale of this row consumed, otherwise null. */
  @JsonIgnore
  public List<CostLot> transactionConsumedLots;

  /** True when the lots did not cover the sale of this row, otherwise null. */
  @JsonIgnore
  public Boolean transactionCostBasisIncomplete;

  /////////////////////////////////////////////////////////////
  // Lots of the security accounts that calculate by FIFO or LIFO. A security account on average cost has no
  // ledger, its purchases stay in the pooled remainder of adjustedCostBase and units.
  ////////////////////////////////////////////////////////////
  @JsonIgnore
  private final Map<Integer, CostLedger> costLedgers = new HashMap<>();

  /** Units of all ledgers, the part of {@link #units} that does not belong to the average cost pool. */
  @JsonIgnore
  private double ledgerUnits;

  /** Cost of all ledgers, the part of {@link #adjustedCostBase} that does not belong to the average cost pool. */
  @JsonIgnore
  private double ledgerCost;

  /** Exchange rate from security currency to main currency that {@link #calcMainCurrency(double)} was called with. */
  @JsonIgnore
  public double reportExchangeRate;

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

  /** Report percentages are rounded on output so allocation calculations retain their original precision. */
  public Double getTargetPercentage() {
    return targetPercentage == null ? null : DataBusinessHelper.roundPercentage(targetPercentage);
  }

  public Double getActualPercentage() {
    return actualPercentage == null ? null : DataBusinessHelper.roundPercentage(actualPercentage);
  }

  public Double getDeviationPercentage() {
    return deviationPercentage == null ? null : DataBusinessHelper.roundPercentage(deviationPercentage);
  }

  public Double getParentDeviation() {
    return parentDeviation == null ? null : DataBusinessHelper.roundPercentage(parentDeviation);
  }

  public Double getSecurityDeviationPercentage() {
    return securityDeviationPercentage == null ? null : DataBusinessHelper.roundPercentage(securityDeviationPercentage);
  }

  public Double getShareOfTotalPercentage() {
    return shareOfTotalPercentage == null ? null : DataBusinessHelper.roundPercentage(shareOfTotalPercentage);
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

  /**
   * The realized part of {@link #gainLossSecurity}, unrounded: what income and the open position do not explain. For a
   * security these are the gains of its sales, for a margin instrument the closed positions with all finance costs.
   */
  public double realizedGainLossSecurity() {
    return gainLossSecurity - incomeGainLossSecurity - unrealizedGainLossSecurity;
  }

  /** The realized part of {@link #gainLossSecurityMC}, unrounded, at the exchange rate of the report. */
  public double realizedGainLossSecurityMC() {
    return gainLossSecurityMC - incomeGainLossSecurityMC - unrealizedGainLossSecurityMC;
  }

  @Schema(description = """
      The part of gainLossSecurity realized by sales, for a margin instrument by closed positions including finance
      costs. With incomeGainLossSecurity and unrealizedGainLossSecurity it adds up to gainLossSecurity.""")
  public double getRealizedGainLossSecurity() {
    return DataHelper.round(realizedGainLossSecurity(), precision);
  }

  @Schema(description = """
      realizedGainLossSecurity at the exchange rate of the report, not the sum of the sale gains at the rates of the
      sale days.""")
  public double getRealizedGainLossSecurityMC() {
    return DataHelper.round(realizedGainLossSecurityMC(), precisionMC);
  }

  public double getIncomeGainLossSecurity() {
    return DataHelper.round(incomeGainLossSecurity, precision);
  }

  public double getIncomeGainLossSecurityMC() {
    return DataHelper.round(incomeGainLossSecurityMC, precisionMC);
  }

  public double getUnrealizedGainLossSecurity() {
    return DataHelper.round(unrealizedGainLossSecurity, precision);
  }

  public double getUnrealizedGainLossSecurityMC() {
    return DataHelper.round(unrealizedGainLossSecurityMC, precisionMC);
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

  public double getGainLossCurrencyMC() {
    return DataHelper.round(gainLossCurrencyMC, precisionMC);
  }

  public Double getDisposalTransactionCost() {
    return disposalTransactionCost == null ? null : DataHelper.round(disposalTransactionCost, precision);
  }

  public Double getDisposalTaxCost() {
    return disposalTaxCost == null ? null : DataHelper.round(disposalTaxCost, precision);
  }

  public Double getDisposalFxCost() {
    return disposalFxCost == null ? null : DataHelper.round(disposalFxCost, precision);
  }

  public Double getDisposalCostMC() {
    return disposalCostMC == null ? null : DataHelper.round(disposalCostMC, precisionMC);
  }

  public Double getValueAfterDisposalMC() {
    return valueAfterDisposalMC == null ? null : DataHelper.round(valueAfterDisposalMC, precisionMC);
  }

  /**
   * Takes over the disposal cost estimate of the hypothetical sale. The main currency amounts follow in
   * {@link #calcMainCurrency(double)}.
   *
   * @param estimate the estimate in security currency
   */
  public void applyDisposalEstimate(DisposalEstimate estimate) {
    disposalTransactionCost = estimate.commission();
    disposalTaxCost = estimate.tax();
    disposalFxCost = estimate.fxCost();
    disposalComplete = estimate.complete();
    disposalDetails = estimate.details().isEmpty() ? null : estimate.details();
    disposalSameCurrencyNet = estimate.sameCurrencyNet();
  }

  /**
   * Resets position metrics for open margin calculation scenarios. Used when recalculating margin positions from
   * transaction history, ensuring clean starting state for accurate position reconstruction.
   */
  public void resetForOpenMargin() {
    gainLossSecurity = 0.0;
    incomeGainLossSecurity = 0.0;
    unrealizedGainLossSecurity = 0.0;
    adjustedCostBase = 0.0;
    units = 0.0;
  }

  /**
   * Cost of the average cost pool: what remains of the cost of the position after the lots of the FIFO and LIFO
   * security accounts. Equals {@link #adjustedCostBase} for a position without a ledger.
   */
  @JsonIgnore
  public double averageCost() {
    return adjustedCostBase - ledgerCost;
  }

  /**
   * Units of the average cost pool. Equals {@link #units} for a position without a ledger, so that such a position
   * calculates exactly as it did before ledgers existed.
   */
  @JsonIgnore
  public double averageUnits() {
    return costLedgers.isEmpty() ? units : DataBusinessHelper.round(units - ledgerUnits);
  }

  /**
   * Books a purchase as the newest lot of its security account. Only for a security account that calculates by FIFO
   * or LIFO; {@link #adjustedCostBase} and {@link #units} are maintained by the caller for every method.
   *
   * @param transaction  the purchase
   * @param unitsSplited units of the purchase on the split basis of the report date
   * @param cost         cost of the purchase in security currency, purchase costs included
   * @param exchangeRate rate into the main currency at the purchase, null for a security in the main currency
   */
  public void addToLedger(Transaction transaction, double unitsSplited, double cost, Double exchangeRate) {
    costLedgers.computeIfAbsent(transaction.getIdSecurityaccount(), _ -> new CostLedger()).add(new CostLot(
        transaction.getIdTransaction(), transaction.getTransactionTime(), exchangeRate, unitsSplited, cost));
    ledgerUnits += unitsSplited;
    ledgerCost += cost;
  }

  /**
   * Consumes the lots of the security account of a sale and sets the fields of the row. Units that no lot covers,
   * which can only happen with data that predates the units check, take their proportional share of the proceeds as
   * cost: missing purchases must not show a gain. The row is then marked as incomplete.
   *
   * @param transaction  the sale
   * @param unitsSplited units of the sale on the split basis of the report date
   * @param proceeds     proceeds of the sale in security currency, after its costs
   * @param method       {@link CostBasisMethod#FIFO} or {@link CostBasisMethod#LIFO}
   * @return the cost of the sold units in security currency
   */
  public double reduceFromLedger(Transaction transaction, double unitsSplited, double proceeds,
      CostBasisMethod method) {
    final CostLedger costLedger = costLedgers.get(transaction.getIdSecurityaccount());
    final CostLedger.Reduction reduction = costLedger == null
        ? new CostLedger.Reduction(List.of(), 0.0, unitsSplited)
        : costLedger.reduce(unitsSplited, method);
    double cost = reduction.cost();
    if (reduction.uncoveredUnits() > 0.0) {
      cost += proceeds * reduction.uncoveredUnits() / unitsSplited;
      transactionCostBasisIncomplete = Boolean.TRUE;
    }
    transactionConsumedLots = reduction.consumedLots();
    // The whole sale leaves the ledger totals, also its uncovered part, so that the average cost pool stays untouched
    ledgerUnits -= unitsSplited;
    ledgerCost -= cost;
    return cost;
  }

  /** The ledger of a security account, null when it holds no lots or calculates by average cost. */
  @JsonIgnore
  public CostLedger getCostLedger(Integer idSecurityaccount) {
    return costLedgers.get(idSecurityaccount);
  }

  /**
   * Values the open position at a price and adds the unrealized result to {@link #gainLossSecurity}. For a security
   * that is not a margin instrument the percentage becomes the open return: the unrealized result relative to the
   * remaining cost, without realized gains and income. It is null when the remaining cost is zero.
   *
   * @param price the price in the currency of the security
   */
  public void calcGainLossByPrice(final Double price) {
    valueSecurity = price * (openUnitsTimeValuePerPoint == 0 ? units : openUnitsTimeValuePerPoint);
    if (this.securitycurrency.isMarginInstrument()) {
      calcGainLossByPriceForMargin(price);
      transactionGainLossPercentage = DataBusinessHelper.roundPercentage(gainLossSecurity * 100 / adjustedCostBase);
    } else {
      gainLossSecurity = gainLossSecurity + valueSecurity - units * adjustedCostBase / units;
      unrealizedGainLossSecurity += units == 0.0 ? 0.0 : valueSecurity - adjustedCostBase;
      transactionGainLossPercentage = adjustedCostBase == 0.0 ? null
          : DataBusinessHelper.roundPercentage((valueSecurity - adjustedCostBase) * 100 / adjustedCostBase);
    }
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
    marginGainLossBySettlementCurrency = new HashMap<>();
    for (TransactionsMarginOpenUnits tmou : transactionsMarginOpenUnitsMap.values()) {
      if (!tmou.markForRemove) {
        // No closing costs are deducted, because the disposal cost estimate does not cover margin instruments
        double gainLoss = tmou.calcGainLossOnPositionClose(price, 0);
        openWinLose += gainLoss;
        marginGainLossBySettlementCurrency.merge(tmou.openTransaction.getCashaccount().getCurrency(),
            DataBusinessHelper.roundStandard(gainLoss), Double::sum);
      }
    }
    gainLossSecurity = DataBusinessHelper.roundStandard(gainLossSecurity + openWinLose);
    unrealizedGainLossSecurity += DataBusinessHelper.roundStandard(openWinLose);
    accountValueSecurity += DataBusinessHelper.roundStandard(openWinLose);
  }

  /**
   * Converts security currency values to main currency using the specified exchange rate.
   *
   * <p>
   * Everything here is valued at the one reporting date rate, which is what makes {@link #gainLossSecurityMC} and
   * {@link #gainLossCurrencyMC} add up to the actual main currency result of the position: the first carries the
   * performance measured in the security currency, the second the drift of the invested money against the rates it was
   * originally booked at. The cash account side computes its own currency result the same way, so the two reports
   * measure with the same ruler.
   * </p>
   *
   * @param currencyExchangeRate exchange rate from security currency to main currency
   */
  public void calcMainCurrency(double currencyExchangeRate) {
    reportExchangeRate = currencyExchangeRate;
    gainLossSecurityMC = gainLossSecurity * currencyExchangeRate;
    incomeGainLossSecurityMC = incomeGainLossSecurity * currencyExchangeRate;
    unrealizedGainLossSecurityMC = unrealizedGainLossSecurity * currencyExchangeRate;
    gainLossCurrencyMC = netInvestedSC * currencyExchangeRate - netInvestedMC;
    valueSecurityMC = valueSecurity * currencyExchangeRate;
    if (securitycurrency.isMarginInstrument()) {
      accountValueSecurityMC = accountValueSecurity * currencyExchangeRate;
      if (marginGainLossBySettlementCurrency != null) {
        marginGainLossBySettlementCurrencyMC = new HashMap<>();
        marginGainLossBySettlementCurrency.forEach((currency, gainLoss) -> marginGainLossBySettlementCurrencyMC
            .put(currency, gainLoss * currencyExchangeRate));
      }
    } else {
      accountValueSecurity = valueSecurity;
      accountValueSecurityMC = valueSecurityMC;

    }
    transactionCostMC = transactionCost * currencyExchangeRate;
    taxCostMC = taxCost * currencyExchangeRate;
    excludedDivTaxMC = excludedDivTax * currencyExchangeRate;
    if (disposalComplete != null) {
      disposalCostMC = ((disposalTransactionCost == null ? 0.0 : disposalTransactionCost)
          + (disposalTaxCost == null ? 0.0 : disposalTaxCost) + (disposalFxCost == null ? 0.0 : disposalFxCost))
          * currencyExchangeRate;
      valueAfterDisposalMC = accountValueSecurityMC - disposalCostMC;
    }

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
   * @return percentage gain/loss for the position, null for a security that is not a margin instrument when the
   *         percentage is undefined because the position has no cost
   */
  @JsonIgnore
  public Double getPositionGainLossPercentage() {
    if (securitycurrency.isMarginInstrument()) {
      return DataBusinessHelper.roundPercentage(this.gainLossSecurity / this.adjustedCostBase * 100);
    }
    return transactionGainLossPercentage == null ? null
        : DataBusinessHelper.roundPercentage(transactionGainLossPercentage);
  }

  /**
   * The amount in security currency behind {@link #getPositionGainLossPercentage()}: the unrealized gain or loss of the
   * open margin positions, otherwise the gain or loss of the hypothetical sale of the open units.
   *
   * @return gain/loss of the open position in security currency, null when no hypothetical sale was calculated
   */
  @JsonIgnore
  public Double getPositionGainLoss() {
    return securitycurrency.isMarginInstrument() ? gainLossSecurity : transactionGainLoss;
  }

  /**
   * Gain or loss of the open position in main currency including the currency effect: the price gain valued at the
   * reporting date rate plus {@link #gainLossCurrencyMC}, the drift of the money still invested against the rates it
   * was originally booked at. A margin position settles only its gain or loss, no capital was converted, so it is the
   * price gain at the reporting date rate. Requires {@link #calcMainCurrency(double)} to have run.
   *
   * @return gain/loss in main currency, null when no hypothetical sale was calculated
   */
  @JsonIgnore
  public Double getPositionGainLossMC() {
    final Double positionGainLoss = getPositionGainLoss();
    if (positionGainLoss == null) {
      return null;
    }
    return DataHelper.round(
        positionGainLoss * reportExchangeRate + (securitycurrency.isMarginInstrument() ? 0.0 : gainLossCurrencyMC),
        precisionMC);
  }

  /**
   * {@link #getPositionGainLossMC()} relative to the cost basis of the open units in main currency. The cost basis is
   * converted at the average rate at which the money still invested was booked; when nothing is invested any more,
   * because dividends and sales have returned more than was paid, the reporting date rate takes its place. For a
   * security in main currency the result equals {@link #getPositionGainLossPercentage()}, as it does for a margin
   * position. Requires {@link #calcMainCurrency(double)} to have run.
   *
   * @return percentage gain/loss in main currency, null when it cannot be determined
   */
  @JsonIgnore
  public Double getPositionGainLossPercentageMC() {
    if (securitycurrency.isMarginInstrument()) {
      return getPositionGainLossPercentage();
    }
    final Double positionGainLossMC = getPositionGainLossMC();
    if (positionGainLossMC == null || transactionCostBase == null) {
      return null;
    }
    final double averageRate = netInvestedSC > 0 ? netInvestedMC / netInvestedSC : reportExchangeRate;
    final double costBaseMC = transactionCostBase * averageRate;
    return costBaseMC == 0 ? null : DataBusinessHelper.roundPercentage(positionGainLossMC / costBaseMC * 100);
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
        + openUnitsTimeValuePerPoint + ", netInvestedSC=" + netInvestedSC + ", netInvestedMC=" + netInvestedMC
        + ", gainLossCurrencyMC=" + gainLossCurrencyMC + ", transactionGainLoss=" + transactionGainLoss
        + ", transactionGainLossPercentage=" + transactionGainLossPercentage + ", transactionGainLossMC="
        + transactionGainLossMC + ", transactionExchangeRate=" + transactionExchangeRate
        + ", transactionsMarginOpenUnitsMap=" + transactionsMarginOpenUnitsMap + ", transactionFlowSC="
        + transactionFlowSC + "]";
  }

}
