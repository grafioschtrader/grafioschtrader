package grafioschtrader.instrument;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.common.DataHelper;
import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary.TransactionsMarginOpenUnits;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.TransactionType;

/**
 * Specialized calculator for margin trading instruments including CFDs (Contracts for Difference) and Forex positions.
 * 
 * <p>
 * This class implements sophisticated position tracking and gain/loss calculations for leveraged financial instruments
 * where traders can open multiple positions that are tracked individually. Unlike standard securities that use average
 * cost accounting, margin instruments require specific position tracking to accurately calculate gains and losses for
 * partial position closures and complex trading strategies.
 * </p>
 * 
 * <h3>Margin Trading Characteristics:</h3>
 * <ul>
 * <li><strong>Leverage:</strong> Positions can be leveraged with multipliers affecting exposure and risk</li>
 * <li><strong>Individual Position Tracking:</strong> Each opening transaction creates a separate trackable position</li>
 * <li><strong>Partial Closures:</strong> Positions can be closed in multiple transactions with precise attribution</li>
 * <li><strong>Finance Costs:</strong> Daily holding costs for leveraged positions are tracked separately</li>
 * <li><strong>Directional Trading:</strong> Supports both long (buy) and short (sell) position strategies</li>
 * <li><strong>Value Per Point:</strong> CFDs use point-based pricing with configurable value per point</li>
 * </ul>
 * 
 * <h3>Position Lifecycle Management:</h3>
 * <p>
 * The calculator manages the complete lifecycle of margin positions:
 * </p>
 * <ol>
 * <li><strong>Opening:</strong> Creates a new trackable position with leverage and cost basis</li>
 * <li><strong>Holding:</strong> Tracks daily finance costs and mark-to-market valuations</li>
 * <li><strong>Partial Closing:</strong> Allows precise calculation of gains/losses for position portions</li>
 * <li><strong>Full Closure:</strong> Completes position tracking and calculates final performance</li>
 * </ol>
 * 
 * <h3>Risk and Exposure Calculation:</h3>
 * <p>
 * For margin instruments, the system distinguishes between account value (actual cash impact) and security risk 
 * (total exposure including leverage). This ensures accurate portfolio risk assessment and regulatory compliance 
 * for leveraged trading activities.
 * </p>
 * 
 * <h3>Multi-Currency Support:</h3>
 * <p>
 * Fully supports multi-currency margin trading with automatic conversion to portfolio base currency, including
 * currency gain/loss attribution separate from trading performance.
 * </p>
 * 
 * <h3>Corporate Actions:</h3>
 * <p>
 * Handles security splits and other corporate actions that affect margin positions, ensuring position integrity
 * across complex corporate events while maintaining accurate leverage and exposure calculations.
 * </p>
 */ 
public class SecurityMarginCalc extends SecurityBaseCalc {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Processes a single margin instrument transaction and updates the corresponding position summary.
   * 
   * <p>
   * This method handles the margin trading by tracking individual positions rather than using
   * average cost accounting. Each opening transaction creates a separate position that can be partially or
   * fully closed in subsequent transactions, allowing for precise performance attribution and risk management.
   * </p>
   * 
   * @param transaction the margin transaction to process
   * @param summarySecurityMap map of existing position summaries, updated with transaction results
   * @param securitysplitMap map of security splits for corporate action adjustments
   * @param excludeDivTaxcost currently not applicable for margin instruments, included for interface compatibility
   * @param dateCurrencyMap currency exchange rate data for multi-currency margin trading
   * @param negativeIdNumberCreater utility for generating IDs for derived transactions
   * @param currencyPrecisionMap precision settings for different currencies used in calculations
   */ 
  @Override
  void calcSingleSecurityTransaction(Transaction transaction, Map<Security, SecurityPositionSummary> summarySecurityMap,
      Map<Integer, List<Securitysplit>> securitysplitMap, boolean excludeDivTaxcost,
      DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater,
      Map<String, Integer> currencyPrecisionMap) {
    SecurityPositionSummary securityPositionSummary = getSecurityPositionSummary(transaction, summarySecurityMap,
        dateCurrencyMap, currencyPrecisionMap);
    calcTransactionPosition(transaction, securityPositionSummary, excludeDivTaxcost, securitysplitMap, true,
        dateCurrencyMap);
  }

  /**
   * Creates hypothetical closing transactions for all open margin positions to simulate portfolio valuation.
   * 
   * <p>
   * Unlike standard securities that create a single hypothetical sale, margin instruments may have multiple
   * open positions that need individual closing transactions. This method iterates through all open positions
   * and creates appropriate hypothetical transactions based on the original position direction (long or short).
   * </p>
   * 
   * <h4>Position Direction Handling:</h4>
   * <ul>
   * <li><strong>Long Positions (ACCUMULATE):</strong> Creates hypothetical sell transactions</li>
   * <li><strong>Short Positions (REDUCE):</strong> Creates hypothetical buy transactions to cover</li>
   * </ul>
   * 
   * <h4>Valuation Features:</h4>
   * <ul>
   * <li><strong>Individual Position Valuation:</strong> Each open position valued separately for precision</li>
   * <li><strong>Leverage Application:</strong> Applies value-per-point and leverage factors correctly</li>
   * <li><strong>Stale Position Handling:</strong> Recalculates positions if marked for recalculation</li>
   * <li><strong>Mark-to-Market:</strong> Provides current market value including unrealized gains/losses</li>
   * </ul>
   * 
   * @param securityPositionSummary the margin position summary containing open positions to value
   * @param lastPrice the current market price for hypothetical closing transactions
   * @param securitysplitMap map of security splits for corporate action adjustments
   * @param dateCurrencyMap currency exchange rate data for multi-currency valuations
   * @param securityTransactionSummary container for generated hypothetical transactions, or null if not needed
   * @param negativeIdNumberCreater utility for generating unique negative IDs for hypothetical transactions
   */ 
  @Override
  void createHypotheticalSellTransaction(SecurityPositionSummary securityPositionSummary, double lastPrice,
      Map<Integer, List<Securitysplit>> securitysplitMap, DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityTransactionSummary securityTransactionSummary, NegativeIdNumberCreater negativeIdNumberCreater) {

    // Go thru every open transaction
    if (securityPositionSummary.reCalculateOpenPosition) {
      securityPositionSummary.removeClosedMarginPosition();
    }
    securityPositionSummary.resetForOpenMargin();
    for (TransactionsMarginOpenUnits tmou : securityPositionSummary.getTransactionsMarginOpenUnits()) {
      // Only open positions, a security may have more than 1 open position.
      boolean openWasLongBuy = tmou.openTransaction.getTransactionType() == TransactionType.ACCUMULATE;
      TransactionType transactionType = openWasLongBuy ? TransactionType.HYPOTHETICAL_SELL
          : TransactionType.HYPOTHETICAL_BUY;
      Transaction transaction = createHypotheticalBuySellTransaction(transactionType, securityPositionSummary,
          lastPrice, negativeIdNumberCreater);
      transaction.setAssetInvestmentValue2(tmou.openTransaction.getAssetInvestmentValue2());
      transaction.setSecuritycurrency(tmou.openTransaction.getSecurity());
      transaction.setUnits(Math.abs(tmou.openUnits));
      transaction.setConnectedIdTransaction(tmou.openTransaction.getIdTransaction());
      securityPositionSummary.valueSecurity += tmou.openUnits * transaction.getValuePerPoint() * lastPrice;
      if (securityPositionSummary.reCalculateOpenPosition) {
        calcTransactionPosition(tmou.openTransaction, securityPositionSummary, false, securitysplitMap, false,
            dateCurrencyMap);
      }
      calcTransactionPosition(transaction, securityPositionSummary, false, securitysplitMap, false, dateCurrencyMap);
      if (securityTransactionSummary != null) {
        securityTransactionSummary.createAndAddPositionGainLoss(transaction);
      }
    }
  }

  /**
   * Processes a margin transaction and adds the results to both position summary and transaction summary.
   * 
   * <p>
   * This method combines the margin-specific transaction processing with position tracking, ensuring that
   * both cumulative position metrics and individual transaction performance data are properly maintained.
   * This is essential for detailed margin trading analysis and audit trails.
   * </p>
   * 
   * <h4>Integration Points:</h4>
   * <ul>
   * <li><strong>Position Summary:</strong> Updates cumulative position metrics and open position tracking</li>
   * <li><strong>Transaction Summary:</strong> Records individual transaction performance for analysis</li>
   * <li><strong>Audit Trail:</strong> Maintains detailed transaction history for compliance and reporting</li>
   * </ul>
   * 
   * @param transaction the margin transaction to process
   * @param securityTransactionSummary summary container that tracks both position and transaction details
   * @param excludeDivTaxcost tax exclusion flag (not typically applicable to margin instruments)
   * @param securitySplitMap map of security splits for corporate action handling
   * @param dateCurrencyMap currency data for multi-currency margin trading
   * @param negativeIdNumberCreater utility for generating unique IDs
   * 
   * @see SecurityTransactionSummary#createAndAddPositionGainLoss(Transaction)
   */ 
  @Override
  void calcTransactionAndAddToPosition(Transaction transaction, SecurityTransactionSummary securityTransactionSummary,
      boolean excludeDivTaxcost, Map<Integer, List<Securitysplit>> securitySplitMap,
      DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater) {
    calcTransactionPosition(transaction, securityTransactionSummary.securityPositionSummary, excludeDivTaxcost,
        securitySplitMap, false, dateCurrencyMap);
    securityTransactionSummary.createAndAddPositionGainLoss(transaction);

  }

  /**
   * Core calculation method that processes margin transactions based on their type and position relationship.
   * 
   * <p>
   * This private method implements the logic required for margin instrument calculations,
   * handling three distinct transaction categories with different calculation approaches. The method
   * maintains individual position tracking to support complex trading strategies and partial position management.
   * </p>
   * 
   * <h4>Transaction Categories:</h4>
   * <dl>
   * <dt><strong>Opening Positions ({@code isMarginOpenPosition() == true})</strong></dt>
   * <dd>Creates new trackable positions with leverage and expense tracking. Calculates initial security risk
   * and sets up position infrastructure for future partial closures. Only transaction costs affect cash account.</dd>
   * 
   * <dt><strong>Finance Costs ({@link TransactionType#FINANCE_COST})</strong></dt>
   * <dd>Processes daily holding costs for leveraged positions. These costs are attributed to specific open
   * positions and affect overall position performance without changing units or leverage.</dd>
   * 
   * <dt><strong>Closing Positions (connected transactions)</strong></dt>
   * <dd>Handles partial or complete position closures with precise gain/loss attribution. Updates position
   * tracking and removes fully closed positions from active monitoring.</dd>
   * </dl>
   * 
   * <h4>Performance Calculation:</h4>
   * <p>
   * For margin instruments, performance is calculated as the difference between current market value
   * (including leverage) and the adjusted cost basis, with proper attribution to individual opening
   * transactions for partial closures.
   * </p>
   * 
   * @param transaction the margin transaction being processed
   * @param securityPositionSummary the position summary to update with transaction effects
   * @param excludeDivTaxcost flag to exclude dividend tax costs (not applicable to margin instruments)
   * @param securitysplitMap map of security splits for corporate action adjustments
   * @param simulateAccruedRecords flag for accrued interest simulation (not applicable to margin instruments)
   * @param dateCurrencyMap currency exchange rate data for multi-currency support
   */
  private void calcTransactionPosition(final Transaction transaction,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean simulateAccruedRecords,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    TransactionsMarginOpenUnits transactionsMarginOpenUnits = null;
    Map<Integer, TransactionsMarginOpenUnits> transactionsMarginOpen = securityPositionSummary
        .getTransactionsMarginOpenUnitsMap();
    if (transaction.getConnectedIdTransaction() != null) {
      transactionsMarginOpenUnits = transactionsMarginOpen.get(transaction.getConnectedIdTransaction());
    }
    final CalcTransactionPos ctp = initTransactionCalcTransCost(transaction, securityPositionSummary, securitysplitMap,
        dateCurrencyMap, (transactionsMarginOpenUnits == null) ? null : transactionsMarginOpenUnits.openTransaction);
    if (transaction.isMarginOpenPosition()) {
      // Open position
      double cost = (transaction.getTransactionCost() != null) ? transaction.getTransactionCost() : 0.0;
      cost += (transaction.getTaxCost() != null) ? transaction.getTaxCost() : 0.0;
      double expenseIncome = Math.abs(ctp.getRealUntis() * transaction.getValuePerPoint() * transaction.getQuotation())
          + cost;
      transactionsMarginOpenUnits = new TransactionsMarginOpenUnits(transaction, ctp.unitsSplited, expenseIncome,
          ctp.splitFactorFromBaseTransaction);
      transactionsMarginOpen.put(transaction.getIdTransaction(), transactionsMarginOpenUnits);
      securityPositionSummary.units += ctp.unitsSplited;
      securityPositionSummary.openUnitsTimeValuePerPoint += ctp.unitsSplited * transaction.getValuePerPoint();
      // Only tax or transaction cost in cash account amount
      securityPositionSummary.gainLossSecurity += transaction.getCashaccountAmount();
      securityPositionSummary.transactionGainLoss = transaction.getCashaccountAmount();
      this.calcTransactionGainLossMC(ctp, securityPositionSummary, transaction,
          transactionsMarginOpenUnits.expenseIncome, expenseIncome);
    } else if (transaction.getTransactionType() == TransactionType.FINANCE_COST) {
      // Finance cost
      securityPositionSummary.transactionGainLoss = transaction.getCashaccountAmount();
      securityPositionSummary.gainLossSecurity += transaction.getCashaccountAmount();
      this.calcTransactionGainLossMC(ctp, securityPositionSummary, transaction,
          transactionsMarginOpenUnits.expenseIncome, transaction.getCashaccountAmount());
    } else {
      // Close position fully or partly, hypothetical position are possible.
      double gainMarginTransacton = securityPositionSummary.transactionGainLoss = transactionsMarginOpenUnits
          .calcGainLossOnClosePosition(transaction.getQuotation(), ctp.transactionTaxCost, transaction.getUnits(),
              ctp.splitFactorFromBaseTransaction);
      if (transaction.getIdTransaction() < 0) {
        // Close Position -> set a cash account amount
        transaction.setCashaccountAmount(gainMarginTransacton);
      } else {
        securityPositionSummary.units += ctp.unitsSplited;
      }
      securityPositionSummary.gainLossSecurity += gainMarginTransacton;
      calcTransactionGainLossMC(ctp, securityPositionSummary, transaction, transactionsMarginOpenUnits.expenseIncome,
          0);
      transactionsMarginOpenUnits.expenseIncome -= Math
          .abs(ctp.unitsSplited * securityPositionSummary.adjustedCostBase / securityPositionSummary.units);
      securityPositionSummary.openUnitsTimeValuePerPoint += ctp.unitsSplited * transaction.getValuePerPoint();
      transactionsMarginOpenUnits.openUnits += ctp.unitsSplited;
      if (transactionsMarginOpenUnits.openUnits == DataHelper.round(0.0, 4) && transaction.getIdTransaction() > 0) {
        // Remove from open position when no more units left
        transactionsMarginOpenUnits.markForRemove = true;
      }
    }
    log.debug("Transaction-Type: {}, id-Trans: {}, adjustedCostBase-P: {}, openunits-P: {}",
        transaction.getTransactionType(), transaction.getIdTransaction(), transactionsMarginOpenUnits.expenseIncome,
        transactionsMarginOpenUnits.openUnits);
  }

  /**
   * Calculates transaction gain/loss values in the main currency for margin instrument reporting.
   * 
   * <p>
   * This method handles the currency conversion and percentage calculation specific to margin trading,
   * where the relationship between account value and security exposure may differ due to leverage.
   * It ensures that both security currency and main currency values are properly maintained for
   * multi-currency portfolio reporting.
   * </p>
   * 
   * <h4>Calculation Components:</h4>
   * <ul>
   * <li><strong>Adjusted Cost Base:</strong> Updates the cumulative cost basis with new transaction effects</li>
   * <li><strong>Percentage Calculation:</strong> Computes percentage gain/loss relative to position expense/income</li>
   * <li><strong>Currency Conversion:</strong> Applies exchange rate to convert to main currency</li>
   * <li><strong>Account Value:</strong> Sets account value for hypothetical transactions used in valuations</li>
   * </ul>
   * 
   * <h4>Margin-Specific Considerations:</h4>
   * <p>
   * The percentage calculation uses the position's expense/income rather than market value because
   * margin instruments track actual cash impact separately from notional exposure. This provides
   * more meaningful performance metrics for leveraged trading strategies.
   * </p>
   * 
   * @param ctp calculation context containing exchange rate and other transaction parameters
   * @param securityPositionSummary the position summary to update with main currency values
   * @param transaction the transaction being processed (used for cash account amount and ID checks)
   * @param expenseIncomePosition the expense/income of the specific position being affected
   * @param expenseIncomeSecurity the expense/income change for the overall security position
   * 
   * @throws ArithmeticException if expenseIncomePosition is zero when calculating percentage
   */
  private void calcTransactionGainLossMC(CalcTransactionPos ctp, final SecurityPositionSummary securityPositionSummary,
      Transaction transaction, double expenseIncomePosition, double expenseIncomeSecurity) {
    securityPositionSummary.adjustedCostBase += expenseIncomeSecurity;
    securityPositionSummary.transactionGainLossPercentage = transaction.getCashaccountAmount() / expenseIncomePosition
        * 100;
    if (ctp.exchangeRate != null) {
      securityPositionSummary.transactionGainLossMC = securityPositionSummary.transactionGainLoss * ctp.exchangeRate;
    } else {
      securityPositionSummary.transactionGainLossMC = securityPositionSummary.transactionGainLoss;
    }
    if (transaction.getIdTransaction() < 0) {
      // Hypothetical transaction
      securityPositionSummary.accountValueSecurityMC = securityPositionSummary.transactionGainLossMC;
    }
  }

}
