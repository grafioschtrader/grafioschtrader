package grafioschtrader.instrument;

import java.util.List;
import java.util.Map;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TransactionType;

/**
 * Calculator for standard investment securities using average cost accounting methodology.
 * 
 * <p>
 * This class implements position tracking and gain/loss calculations for traditional investment instruments
 * including stocks, bonds, ETFs, mutual funds, and money market securities. Unlike margin instruments that
 * require individual position tracking, standard securities use the average cost method where all units
 * are pooled together with a weighted average cost basis.
 * </p>
 * 
 * <h3>Average Cost Accounting Method:</h3>
 * <p>
 * The calculator employs the industry-standard average cost method for position management:
 * </p>
 * <ul>
 * <li><strong>Cost Basis Pooling:</strong> All purchase transactions contribute to a single pooled cost basis</li>
 * <li><strong>Weighted Average:</strong> New purchases adjust the average cost per unit based on quantities and prices</li>
 * <li><strong>Proportional Allocation:</strong> Sales reduce units and cost basis proportionally</li>
 * <li><strong>FIFO Alternative:</strong> Provides a simplified alternative to complex FIFO tracking</li>
 * </ul>
 * 
 * <h3>Supported Instrument Types:</h3>
 * <dl>
 * <dt><strong>Equities (Stocks)</strong></dt>
 * <dd>Standard stock trading with dividend processing and corporate action adjustments. Supports both domestic 
 * and international equities with currency conversion.</dd>
 * 
 * <dt><strong>Fixed Income (Bonds)</strong></dt>
 * <dd>Bond trading with specialized accrued interest handling. Automatically separates accrued interest from 
 * principal transactions for accurate yield-to-maturity calculations and tax reporting.</dd>
 * 
 * <dt><strong>Convertible Bonds</strong></dt>
 * <dd>Hybrid securities combining bond and equity characteristics. Handles both interest payments and potential 
 * conversion features with appropriate accrued interest calculations.</dd>
 * 
 * <dt><strong>Money Market Securities</strong></dt>
 * <dd>Short-term debt instruments with special pricing considerations. For securities without external price feeds,
 * the system uses transaction prices as closing prices for valuation consistency.</dd>
 * 
 * <dt><strong>Investment Funds (ETFs, Mutual Funds)</strong></dt>
 * <dd>Pooled investment vehicles with dividend distributions and potential capital gains distributions. 
 * Handles both accumulating and distributing fund types.</dd>
 * </dl>
 * 
 * <h3>Performance Calculation:</h3>
 * <p>
 * Gain/loss calculations consider the full total return including:
 * </p>
 * <ul>
 * <li>Capital appreciation/depreciation based on average cost method</li>
 * <li>Dividend and interest income (gross or net of taxes)</li>
 * <li>Currency exchange gains/losses for foreign securities</li>
 * <li>Transaction costs and tax implications</li>
 * </ul>
 */ 
public class SecurityGeneralCalc extends SecurityBaseCalc {

  /**
   * Processes a single transaction for standard investment securities using average cost methodology.
   * 
   * <p>
   * This method handles the complete transaction lifecycle for traditional investment instruments,
   * applying average cost accounting principles to maintain accurate position tracking. The calculation
   * considers corporate actions, currency conversions, and instrument-specific features such as
   * accrued interest for bonds.
   * </p>
   * 
   * @param transaction the transaction to process, containing trade details, pricing, and costs
   * @param summarySecurityMap map of existing position summaries, keyed by security, updated with results
   * @param securitysplitMap map of security splits by security ID for corporate action adjustments
   * @param excludeDivTaxcost if {@code true}, excludes dividend withholding taxes from gain/loss calculations
   * @param dateCurrencyMap currency exchange rate data for multi-currency portfolio calculations
   * @param negativeIdNumberCreater utility for generating unique IDs for derived transactions (e.g., accrued interest)
   * @param currencyPrecisionMap precision settings for different currencies to ensure accurate rounding
   */
  @Override
  void calcSingleSecurityTransaction(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater,
      Map<String, Integer> currencyPrecisionMap) {

    SecurityPositionSummary securityPositionSummary = getSecurityPositionSummary(transaction, summarySecurityMap,
        dateCurrencyMap, currencyPrecisionMap);
    // TODO excludeDivTaxcost
    calcTransactionPosition(transaction, securityPositionSummary, excludeDivTaxcost, securitysplitMap, true,
        dateCurrencyMap, negativeIdNumberCreater);
  }

  /**
   * Creates a hypothetical sell transaction to simulate complete position liquidation for valuation purposes.
   * 
   * <p>
   * Unlike margin instruments that may have multiple open positions, standard securities maintain a single
   * pooled position that can be entirely liquidated with one hypothetical transaction. This method calculates
   * the unrealized gain/loss that would result from selling the entire position at the current market price.
   * </p>
   * 
   * <h4>Simulation Process:</h4>
   * <ol>
   * <li><strong>State Preservation:</strong> Saves current unit count to restore after simulation</li>
   * <li><strong>Transaction Creation:</strong> Generates a hypothetical sell for all units at current price</li>
   * <li><strong>Gain/Loss Calculation:</strong> Computes unrealized performance using average cost method</li>
   * <li><strong>State Restoration:</strong> Returns position to original state while preserving valuation data</li>
   * <li><strong>Summary Recording:</strong> Adds transaction details to summary for reporting</li>
   * </ol>
   * 
   * @param securityPositionSummary the current position to simulate selling
   * @param lastPrice the current market price for the hypothetical sale
   * @param securitysplitMap map of security splits for corporate action adjustments
   * @param dateCurrencyMap currency exchange rate data for multi-currency calculations
   * @param securityTransactionSummary container for the hypothetical transaction details, or null if not needed
   * @param negativeIdNumberCreater utility for generating unique negative IDs for hypothetical transactions
   */
  @Override
  void createHypotheticalSellTransaction(SecurityPositionSummary securityPositionSummary, double lastPrice,
      Map<Integer, List<Securitysplit>> securitysplitMap, DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityTransactionSummary securityTransactionSummary, NegativeIdNumberCreater negativeIdNumberCreater) {

    final double beforeUnits = securityPositionSummary.units;
    Transaction transaction = createHypotheticalBuySellTransaction(TransactionType.HYPOTHETICAL_SELL,
        securityPositionSummary, lastPrice, negativeIdNumberCreater);

    transaction.setUnits(securityPositionSummary.units);
    calcTransactionPosition(transaction, securityPositionSummary, false, securitysplitMap, false, dateCurrencyMap,
        negativeIdNumberCreater);
    securityPositionSummary.units = beforeUnits;
    securityPositionSummary.valueSecurity = beforeUnits * lastPrice;
    if (securityTransactionSummary != null) {
      securityTransactionSummary.createAndAddPositionGainLoss(transaction);
    }
  }

  /**
   * Processes a transaction and integrates the results into both position and transaction summaries.
   * 
   * <p>
   * This method serves as the integration point between transaction processing and summary reporting,
   * ensuring that both cumulative position metrics and individual transaction performance data are
   * properly maintained for comprehensive portfolio analysis and audit trails.
   * </p>
   * 
   * @param transaction the transaction to process and record
   * @param securityTransactionSummary summary container tracking both position and transaction details
   * @param excludeDivTaxcost whether to exclude dividend taxes from gain/loss calculations
   * @param securitySplitMap map of security splits for corporate action handling
   * @param dateCurrencyMap currency exchange rate data for multi-currency support
   * @param negativeIdNumberCreater utility for generating IDs for derived transactions
   */ 
  @Override
  void calcTransactionAndAddToPosition(final Transaction transaction,
      final SecurityTransactionSummary securityTransactionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitySplitMap, final DateTransactionCurrencypairMap dateCurrencyMap,
      NegativeIdNumberCreater negativeIdNumberCreater) {
    calcTransactionPosition(transaction, securityTransactionSummary.securityPositionSummary, excludeDivTaxcost,
        securitySplitMap, false, dateCurrencyMap, negativeIdNumberCreater);
    securityTransactionSummary.createAndAddPositionGainLoss(transaction);
  }

  /**
   * Creates and processes accrued interest transactions for bonds and convertible bonds.
   * 
   * <p>
   * When trading bonds, the purchase or sale price typically includes accrued interest that has accumulated
   * since the last coupon payment. This method automatically separates this accrued interest component into
   * a distinct transaction for accurate accounting, tax reporting, and yield calculations.
   * </p>
   * 
   * <h4>Asset Class Applicability:</h4>
   * <p>
   * This method only processes transactions for:
   * </p>
   * <ul>
   * <li>{@link AssetclassType#FIXED_INCOME} - Traditional bonds with regular coupon payments</li>
   * <li>{@link AssetclassType#CONVERTIBLE_BOND} - Convertible securities with bond-like interest</li>
   * </ul>
   * 
   * <h4>Transaction Flow:</h4>
   * <ol>
   * <li>Validates asset class and transaction type compatibility</li>
   * <li>Checks for non-zero accrued interest amount in transaction</li>
   * <li>Creates separate accrued interest transaction with appropriate direction</li>
   * <li>Processes the accrued interest transaction through normal calculation pipeline</li>
   * </ol>
   * 
   * @param security the security being traded (must be bond or convertible bond)
   * @param requiredTransactionTypeTransaction the transaction type that should trigger accrued interest processing
   * @param transaction the original transaction containing accrued interest data
   * @param securityTransactionSummary summary container to receive the accrued interest transaction
   * @param excludeDivTaxcost whether to exclude taxes from the accrued interest calculation
   * @param securitySplitMap map of security splits for corporate action adjustments
   * @param dateCurrencyMap currency exchange rate data for multi-currency calculations
   * @param negativeIdNumberCreater utility for generating unique IDs for the accrued interest transaction
    */
  void createAccruedInterestPostion(final Security security, final TransactionType requiredTransactionTypeTransaction,
      final Transaction transaction, final SecurityTransactionSummary securityTransactionSummary,
      final boolean excludeDivTaxcost, final Map<Integer, List<Securitysplit>> securitySplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater) {
    if (security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
        || security.getAssetClass().getCategoryType() == AssetclassType.CONVERTIBLE_BOND) {

      if (transaction.getTransactionType() == requiredTransactionTypeTransaction
          && transaction.getAssetInvestmentValue1() != null && transaction.getAssetInvestmentValue1() != 0.0) {
        final Transaction accruedInterestTransaction = createAccruedInterestTransaction(transaction,
            negativeIdNumberCreater);
        calcTransactionAndAddToPosition(accruedInterestTransaction, securityTransactionSummary, excludeDivTaxcost,
            securitySplitMap, dateCurrencyMap, negativeIdNumberCreater);
      }
    }
  }

  /**
   * Creates a separate transaction to represent the accrued interest component of a bond trade.
   * 
   * <p>
   * This method constructs a specialized transaction that isolates the accrued interest portion from
   * the principal component of a bond transaction. The resulting transaction has characteristics
   * appropriate for interest income recognition and can be processed through the normal calculation
   * pipeline while maintaining proper accounting separation.
   * </p>
   * 
   * <h4>Sign Convention:</h4>
   * <p>
   * The cash account amount follows standard accounting conventions:
   * </p>
   * <ul>
   * <li><strong>Purchase (ACCUMULATE):</strong> Negative amount (cash outflow for accrued interest paid)</li>
   * <li><strong>Sale (REDUCE):</strong> Positive amount (cash inflow for accrued interest received)</li>
   * </ul>
   * 
   * @param transaction the original bond transaction containing accrued interest data
   * @param negativeIdNumberCreater utility for generating a unique negative ID for the accrued interest transaction
   * @return a fully configured accrued interest transaction ready for processing
   */
  private Transaction createAccruedInterestTransaction(final Transaction transaction,
      NegativeIdNumberCreater negativeIdNumberCreater) {
    final Transaction accruedInterestTransaction = new Transaction();
    accruedInterestTransaction.setCashaccountAmount(transaction.getAssetInvestmentValue1()
        * (transaction.getTransactionType() == TransactionType.ACCUMULATE ? -1.0 : 1.0));
    accruedInterestTransaction.setIdTransaction(negativeIdNumberCreater.getNegativeId());
    accruedInterestTransaction.setTransactionType(TransactionType.ACCRUED_INTEREST);
    accruedInterestTransaction.setUnits(transaction.getUnits());
    accruedInterestTransaction.setQuotation(accruedInterestTransaction.getCashaccountAmount() / transaction.getUnits());
    accruedInterestTransaction.setTransactionTime(transaction.getTransactionTime());
    accruedInterestTransaction.setSecuritycurrency(transaction.getSecurity());
    return accruedInterestTransaction;
  }

  /**
   * Core calculation method that processes all transaction types for standard securities using average cost accounting.
   * 
   * <p>
   * This method implements the central calculation logic for standard investment securities, coordinating
   * the application of average cost methodology across different transaction types. It handles the complexity
   * of multi-currency calculations, corporate action adjustments, and instrument-specific features while
   * maintaining the mathematical integrity of the average cost method.
   * </p>
   * 
   * @param transaction the transaction to process
   * @param securityPositionSummary the position summary to update with transaction effects
   * @param excludeDivTaxcost whether to exclude dividend withholding taxes from calculations
   * @param securitysplitMap map of security splits for corporate action adjustments
   * @param simulateAccruedRecords whether to create simulated accrued interest transactions for bonds
   * @param dateCurrencyMap currency exchange rate data for multi-currency calculations
   * @param negativeIdNumberCreater utility for generating IDs for derived transactions
   */
  private void calcTransactionPosition(final Transaction transaction,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean simulateAccruedRecords,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater) {

    final CalcTransactionPos ctp = initTransactionCalcTransCost(transaction, securityPositionSummary, securitysplitMap,
        dateCurrencyMap, null);

    switch (transaction.getTransactionType()) {
    case ACCUMULATE:
      accumulate(transaction, ctp, securityPositionSummary, excludeDivTaxcost, simulateAccruedRecords,
          negativeIdNumberCreater);
      break;
    case REDUCE:
    case HYPOTHETICAL_SELL:
      reduceOrHypotheticalSell(transaction, ctp, securityPositionSummary, excludeDivTaxcost, simulateAccruedRecords,
          negativeIdNumberCreater);
      break;
    case DIVIDEND:
    case ACCRUED_INTEREST:
      calcDividend(transaction, securityPositionSummary, excludeDivTaxcost, ctp.transactionCost, ctp.taxCost,
          ctp.exchangeRate);
      break;

    default:
      break;
    }
    securityPositionSummary.roundUnits();
    securityPositionSummary.transactionGainLoss = (securityPositionSummary.transactionGainLoss != null)
        ? DataBusinessHelper.round(securityPositionSummary.transactionGainLoss)
        : null;
  }

  /**
   * Processes purchase (accumulate) transactions using average cost methodology.
   * 
   * <p>
   * This method implements the purchase leg of average cost accounting, where new acquisitions are added
   * to the existing position pool. The method calculates the new weighted average cost basis and updates
   * all relevant position metrics including multi-currency tracking and instrument-specific features.
   * </p>
   * 
   * <h4>Average Cost Calculation:</h4>
   * <p>
   * The new average cost is calculated as:
   * <code>(Previous Cost Basis + New Purchase Cost) / (Previous Units + New Units)</code>
   * </p>
   * 
   * <h4>Processing Steps:</h4>
   * <ol>
   * <li><strong>Cost Basis Update:</strong> Adds transaction cost (price × units + fees + taxes) to total cost basis</li>
   * <li><strong>Units Update:</strong> Adds split-adjusted units to total position</li>
   * <li><strong>Price Setting:</strong> For money market securities without price feeds, uses transaction price</li>
   * <li><strong>Accrued Interest:</strong> Simulates accrued interest transactions for bonds if enabled</li>
   * <li><strong>Currency Tracking:</strong> Updates foreign currency balances and main currency equivalents</li>
   * </ol>
   * 
   * <h4>Money Market Special Handling:</h4>
   * <p>
   * For money market securities that lack external price feeds, the transaction price is used as the
   * closing price to ensure accurate position valuation in subsequent calculations.
   * </p>
   * 
   * @param transaction the purchase transaction to process
   * @param ctp calculation context with exchange rates, split factors, and adjusted values
   * @param securityPositionSummary the position summary to update
   * @param excludeDivTaxcost whether to exclude dividend taxes (not applicable to purchases)
   * @param simulateAccruedRecords whether to create accrued interest transactions for bonds
   * @param negativeIdNumberCreater utility for generating IDs for simulated transactions
   */
  private void accumulate(final Transaction transaction, final CalcTransactionPos ctp,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost,
      final boolean simulateAccruedRecords, final NegativeIdNumberCreater negativeIdNumberCreater) {
    final Security security = transaction.getSecurity();
    securityPositionSummary.adjustedCostBase += ctp.securitiesNetPrice + ctp.transactionTaxCost;
    securityPositionSummary.units += ctp.unitsSplited;

    if (security.getAssetClass().getCategoryType() == AssetclassType.MONEY_MARKET
        && (security.getIdConnectorHistory() == null || security.getIdConnectorHistory().isEmpty())
        && (security.getIdConnectorIntra() == null || security.getIdConnectorIntra().isEmpty())) {
      securityPositionSummary.closePrice = transaction.getQuotation();
    }
    if (simulateAccruedRecords) {
      simulateAccruedInterest(transaction, securityPositionSummary, excludeDivTaxcost, ctp.exchangeRate,
          negativeIdNumberCreater);
    }
    if (ctp.exchangeRate != null) {
      securityPositionSummary.balanceSecurityCurrency += ctp.securitiesNetPrice + ctp.transactionTaxCost;
      securityPositionSummary.adjustedCostBaseMC += (ctp.securitiesNetPrice + ctp.transactionTaxCost)
          * ctp.exchangeRate;
    }
  }

  /**
   * Processes sale (reduce) and hypothetical sale transactions using average cost methodology.
   * 
   * <p>
   * This method implements the sale leg of average cost accounting, where units are removed from the
   * position pool proportionally. The method calculates realized gains/losses based on the difference
   * between sale proceeds and the proportional average cost basis, including comprehensive currency
   * gain/loss attribution for foreign securities.
   * </p>
   * 
   * <h4>Average Cost Allocation:</h4>
   * <p>
   * The cost basis allocated to the sale is calculated as:
   * <code>(Units Sold / Total Units) × Total Cost Basis</code>
   * </p>
   * 
   * <h4>Gain/Loss Calculation:</h4>
   * <p>
   * Realized gain/loss = Net Proceeds - Allocated Cost Basis - Transaction Costs - Taxes
   * </p>
   * 
   * <h4>Currency Attribution:</h4>
   * <p>
   * For foreign securities, the method separates total performance into:
   * </p>
   * <ul>
   * <li><strong>Security Performance:</strong> Gain/loss in the security's native currency</li>
   * <li><strong>Currency Performance:</strong> Gain/loss from currency exchange rate movements</li>
   * </ul>
   * 
   * @param transaction the sale transaction to process
   * @param ctp calculation context with exchange rates, split factors, and adjusted values
   * @param securityPositionSummary the position summary to update
   * @param excludeDivTaxcost whether to exclude dividend taxes from gain/loss calculations
   * @param simulateAccruedRecords whether to create accrued interest transactions for bonds
   * @param negativeIdNumberCreater utility for generating IDs for simulated transactions
   */ 
  private void reduceOrHypotheticalSell(final Transaction transaction, final CalcTransactionPos ctp,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost,
      final boolean simulateAccruedRecords, final NegativeIdNumberCreater negativeIdNumberCreater) {
    if (simulateAccruedRecords) {
      simulateAccruedInterest(transaction, securityPositionSummary, excludeDivTaxcost, ctp.exchangeRate,
          negativeIdNumberCreater);
    }
    final double acb = ctp.unitsSplited * securityPositionSummary.adjustedCostBase / securityPositionSummary.units;
    double balance = ctp.securitiesNetPrice - ctp.transactionTaxCost;
    securityPositionSummary.transactionGainLoss = balance - acb;
    securityPositionSummary.transactionGainLossPercentage = securityPositionSummary.transactionGainLoss
        / (ctp.unitsSplited / securityPositionSummary.units * securityPositionSummary.adjustedCostBase) * 100.0;
    securityPositionSummary.adjustedCostBase -= acb;
    securityPositionSummary.units -= ctp.unitsSplited;
    securityPositionSummary.gainLossSecurity += securityPositionSummary.transactionGainLoss;
    if (ctp.exchangeRate != null) {
      double gainLostMC = securityPositionSummary.transactionGainLoss * ctp.exchangeRate;
      securityPositionSummary.transactionGainLossMC = gainLostMC;

      securityPositionSummary.transactionCurrencyGainLossMC = balance * ctp.exchangeRate
          - balance * securityPositionSummary.adjustedCostBaseMC / securityPositionSummary.balanceSecurityCurrency;
      securityPositionSummary.adjustedCostBaseMC -= gainLostMC * securityPositionSummary.adjustedCostBaseMC
          / securityPositionSummary.balanceSecurityCurrency;
      securityPositionSummary.balanceSecurityCurrency -= securityPositionSummary.transactionGainLoss;
      securityPositionSummary.currencyGainLossMC += securityPositionSummary.transactionCurrencyGainLossMC;
    }
  }

  /**
   * Simulates accrued interest transactions for bond trades during calculation processing.
   * 
   * <p>
   * This method creates and processes simulated accrued interest transactions for bonds and convertible
   * bonds during the calculation phase. Unlike the formal accrued interest position creation, this
   * simulation is used for internal calculation consistency and doesn't create persistent transaction
   * records.
   * </p>
   * 
   * <h4>Simulation Purpose:</h4>
   * <ul>
   * <li><strong>Calculation Consistency:</strong> Ensures accrued interest is properly handled in all calculation paths</li>
   * <li><strong>Internal Processing:</strong> Maintains consistent logic between different calculation scenarios</li>
   * <li><strong>Position Accuracy:</strong> Improves accuracy of position calculations that involve accrued interest</li>
   * </ul>
   * 
   * <h4>Asset Class Filtering:</h4>
   * <p>
   * Only processes transactions for bond-like securities that may have accrued interest components:
   * </p>
   * <ul>
   * <li>{@link AssetclassType#FIXED_INCOME}</li>
   * <li>{@link AssetclassType#CONVERTIBLE_BOND}</li>
   * </ul>
   * 
   * @param transaction the original transaction that may contain accrued interest
   * @param securityPositionSummary the position summary to update with simulated accrued interest
   * @param excludeDivTaxcost whether to exclude taxes from the accrued interest calculation
   * @param exchangeRate the exchange rate for currency conversion, or null if not applicable
   * @param negativeIdNumberCreater utility for generating IDs for simulated transactions
   */ 
  private void simulateAccruedInterest(final Transaction transaction,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost, final Double exchangeRate,
      NegativeIdNumberCreater negativeIdNumberCreater) {
    if (transaction.getSecurity().getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
        || transaction.getSecurity().getAssetClass().getCategoryType() == AssetclassType.CONVERTIBLE_BOND) {

      if (transaction.getAssetInvestmentValue1() != null && transaction.getAssetInvestmentValue1() != 0.0) {
        calcDividend(createAccruedInterestTransaction(transaction, negativeIdNumberCreater), securityPositionSummary,
            excludeDivTaxcost, 0.0, 0.0, exchangeRate);
      }
    }
  }
  
  
  /**
   * Calculates dividend and interest income transactions without affecting position units or cost basis.
   * 
   * <p>
   * This method processes income-generating transactions that provide cash flow to the investor without
   * changing the fundamental position structure. Unlike buy/sell transactions, dividend and interest
   * payments don't affect the unit count or average cost basis, but they do contribute to total return
   * and may have tax implications.
   * </p>
   * 
   * <h4>Tax Considerations:</h4>
   * <p>
   * The method provides flexibility for tax reporting by allowing exclusion of withholding taxes:
   * </p>
   * <ul>
   * <li><strong>Gross Income:</strong> When {@code excludeDivTaxcost = true}, reports gross dividend income</li>
   * <li><strong>Net Income:</strong> When {@code excludeDivTaxcost = false}, reports net after-tax income</li>
   * </ul>
   * 
   * <h4>Performance Attribution:</h4>
   * <p>
   * The method calculates percentage returns relative to the current cost basis, providing insights into
   * income yield relative to the investment's cost. For positions with zero cost basis, percentage
   * calculation is skipped to avoid division by zero.
   * </p>
   * 
   * <h4>Multi-Currency Handling:</h4>
   * <p>
   * For foreign securities, dividend income is converted to the portfolio's main currency using the
   * appropriate exchange rate, enabling accurate total return calculations across multi-currency portfolios.
   * </p>
   * 
   * @param transaction the dividend or interest transaction to process
   * @param securityPositionSummary the position summary to update with income
   * @param excludeDivTaxcost whether to exclude withholding taxes from the income calculation
   * @param transactionCost any transaction costs associated with the income (typically zero)
   * @param taxCost withholding taxes or other tax costs associated with the income
   * @param exchangeRate exchange rate for converting foreign currency income to main currency
   */ 
  private void calcDividend(final Transaction transaction, final SecurityPositionSummary securityPositionSummary,
      final boolean excludeDivTaxcost, final double transactionCost, final double taxCost, final Double exchangeRate) {
    securityPositionSummary.transactionGainLoss = transaction.getSeucritiesNetPrice()
        - ((excludeDivTaxcost) ? 0.0 : taxCost) - transactionCost;

    securityPositionSummary.transactionGainLossPercentage = (securityPositionSummary.units > 0)
        ? securityPositionSummary.transactionGainLoss / securityPositionSummary.adjustedCostBase * 100.0
        : null;
    securityPositionSummary.gainLossSecurity += securityPositionSummary.transactionGainLoss;
    if (exchangeRate != null && !transaction.getSecurity().getCurrency().equals(securityPositionSummary.mainCurrency)) {
      securityPositionSummary.transactionGainLossMC = securityPositionSummary.transactionGainLoss * exchangeRate;
      // securityPositionSummary.currencyGainLossMC +=
      // securityPositionSummary.transactionCurrencyGainLossMC;
    }
  }

}
