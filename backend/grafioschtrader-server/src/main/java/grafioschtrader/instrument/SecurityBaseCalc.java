package grafioschtrader.instrument;

import java.util.Date;
import java.util.List;
import java.util.Map;

import grafiosch.common.DateHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Securitysplit.SplitFactorAfterBefore;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.TransactionType;

/**
 * Abstract base class for security transaction calculations and portfolio position management.
 * 
 * <p>
 * This class provides the foundation for calculating gains, losses, and position summaries for various types of
 * securities including stocks, bonds, ETFs, and margin instruments. It implements the Template Method pattern, defining
 * the skeleton of calculation algorithms while allowing subclasses to customize specific aspects for different
 * instrument types.
 * </p>
 * 
 * <h3>Core Responsibilities:</h3>
 * <ul>
 * <li><strong>Transaction Processing:</strong> Handles individual security transactions and their impact on portfolio
 * positions</li>
 * <li><strong>Position Calculation:</strong> Maintains running calculations of units, cost basis, and gain/loss for
 * security positions</li>
 * <li><strong>Currency Conversion:</strong> Manages multi-currency calculations using historical and current exchange
 * rates</li>
 * <li><strong>Corporate Actions:</strong> Accounts for security splits and other corporate actions in position
 * calculations</li>
 * <li><strong>Hypothetical Scenarios:</strong> Creates simulated transactions for position analysis and reporting</li>
 * </ul>
 * 
 * <h3>Calculation Methodology:</h3>
 * <p>
 * The class employs different calculation strategies based on instrument type:
 * </p>
 * <ul>
 * <li><strong>Standard Securities:</strong> Uses average cost method for position tracking</li>
 * <li><strong>Margin Instruments:</strong> Employs specific position tracking for leveraged products</li>
 * <li><strong>Multi-Currency:</strong> Converts all values to a main currency for consolidated reporting</li>
 * </ul>
 * 
 * <h3>Subclass Implementation:</h3>
 * <p>
 * Concrete subclasses must implement instrument-specific calculation logic:
 * </p>
 * <ul>
 * <li>{@link SecurityGeneralCalc} - For standard securities (stocks, bonds, ETFs)</li>
 * <li>{@link SecurityMarginCalc} - For margin instruments (CFDs, Forex)</li>
 * </ul>
 * 
 * <h3>Transaction Flow:</h3>
 * <p>
 * Typical usage involves processing transactions in chronological order, where each transaction updates the position
 * summary with new units, costs, and gain/loss calculations. The class handles complex scenarios including partial
 * position closures, dividend payments, and currency conversions.
 * </p>
 * 
 * <h3>Thread Safety:</h3>
 * <p>
 * This class is not thread-safe. Instances should not be shared across multiple threads without external
 * synchronization. Each calculation sequence should use a dedicated instance or proper synchronization.
 * </p>
 */
public abstract class SecurityBaseCalc {

  /**
   * Calculates the impact of a single security transaction on the portfolio position.
   * 
   * <p>
   * This method processes an individual transaction and updates the corresponding security position summary with new
   * units, cost basis adjustments, gain/loss calculations, and currency conversions. The calculation considers security
   * splits, transaction costs, taxes, and exchange rate fluctuations.
   * </p>
   * 
   * <h4>Processing Steps:</h4>
   * <ol>
   * <li>Retrieves or creates the security position summary</li>
   * <li>Calculates split-adjusted units and prices</li>
   * <li>Applies transaction costs and taxes</li>
   * <li>Updates position metrics (units, cost basis, gain/loss)</li>
   * <li>Performs currency conversions if applicable</li>
   * </ol>
   * 
   * @param transaction             the transaction to process, containing details such as units, price, costs, and
   *                                timing
   * @param summarySecurityMap      a map of security positions keyed by {@link Security}, updated with transaction
   *                                results
   * @param securitysplitMap        map of security splits by security ID, used for corporate action adjustments
   * @param excludeDivTaxcost       if {@code true}, excludes dividend tax costs from gain/loss calculations
   * @param dateCurrencyMap         currency exchange rate data for multi-currency conversions, or {@code null} for
   *                                single currency
   * @param negativeIdNumberCreater utility for generating unique negative IDs for hypothetical transactions
   * @param currencyPrecisionMap    map of currency codes to their respective decimal precision for rounding
   */
  abstract void calcSingleSecurityTransaction(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater,
      Map<String, Integer> currencyPrecisionMap);

  /**
   * Creates hypothetical sell transactions to simulate position closure for analysis purposes.
   * 
   * <p>
   * This method generates simulated sale transactions based on current market prices to show potential gains or losses
   * if the position were closed. For standard securities, this creates a single sell transaction. For margin
   * instruments, this may create multiple transactions corresponding to different open positions.
   * </p>
   * 
   * <h4>Use Cases:</h4>
   * <ul>
   * <li><strong>Portfolio Valuation:</strong> Mark-to-market calculations for open positions</li>
   * <li><strong>Risk Assessment:</strong> Potential exposure analysis</li>
   * <li><strong>Performance Reporting:</strong> Unrealized gain/loss calculations</li>
   * <li><strong>What-if Analysis:</strong> Scenario planning for position management</li>
   * </ul>
   * 
   * @param securityPositionSummary    the current position summary to be analyzed for hypothetical closure
   * @param lastPrice                  the current market price to use for the hypothetical sale calculation
   * @param securitysplitMap           map of security splits for adjusting historical transactions
   * @param dateCurrencyMap            currency exchange rate data for multi-currency scenarios
   * @param securityTransactionSummary container for adding the generated hypothetical transactions
   * @param negativeIdNumberCreater    utility for generating unique negative IDs for hypothetical transactions
   */
  abstract void createHypotheticalSellTransaction(final SecurityPositionSummary securityPositionSummary,
      final double lastPrice, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, final SecurityTransactionSummary securityTransactionSummary,
      NegativeIdNumberCreater negativeIdNumberCreater);

  /**
   * Processes a transaction and adds the resulting position data to the transaction summary.
   * 
   * <p>
   * This method combines transaction calculation with position tracking, ensuring that both individual transaction
   * metrics and cumulative position data are properly maintained. It's typically used in transaction processing
   * workflows where detailed transaction-by-transaction analysis is required.
   * </p>
   * 
   * <h4>Key Features:</h4>
   * <ul>
   * <li>Updates the position summary with transaction effects</li>
   * <li>Records transaction-specific gain/loss metrics</li>
   * <li>Maintains transaction history for audit purposes</li>
   * <li>Handles corporate action adjustments</li>
   * </ul>
   * 
   * @param transaction                the transaction to process and add to the summary
   * @param securityTransactionSummary the summary container that tracks both position and individual transactions
   * @param excludeDivTaxcost          whether to exclude dividend tax costs from calculations
   * @param securitySplitMap           map of security splits by security ID for corporate action handling
   * @param dateCurrencyMap            currency exchange rate data for multi-currency support
   * @param negativeIdNumberCreater    utility for generating unique IDs for derived transactions
   */
  abstract void calcTransactionAndAddToPosition(final Transaction transaction,
      final SecurityTransactionSummary securityTransactionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitySplitMap, final DateTransactionCurrencypairMap dateCurrencyMap,
      NegativeIdNumberCreater negativeIdNumberCreater);

  /**
   * Retrieves or creates a security position summary for the given transaction.
   * 
   * <p>
   * This method implements a lazy initialization pattern, creating new position summaries only when they don't already
   * exist in the map. It ensures that each security has a single position summary that accumulates all transaction
   * effects over time.
   * </p>
   * 
   * <h4>Position Summary Initialization:</h4>
   * <ul>
   * <li>Uses the main currency from the currency map if available</li>
   * <li>Associates the position with the transaction's security account</li>
   * <li>Configures currency precision for accurate calculations</li>
   * <li>Initializes all position metrics to zero</li>
   * </ul>
   * 
   * @param transaction          the transaction requiring a position summary
   * @param summarySecurityMap   the map of existing position summaries, keyed by security
   * @param dateCurrencyMap      currency data containing the main currency for multi-currency portfolios
   * @param currencyPrecisionMap precision settings for different currencies
   * @return existing position summary or a newly created one if none exists
   * 
   * @throws NullPointerException if transaction or transaction.getSecurity() is null
   */
  SecurityPositionSummary getSecurityPositionSummary(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, Map<String, Integer> currencyPrecisionMap) {
    return summarySecurityMap.computeIfAbsent(transaction.getSecurity(),
        key -> new SecurityPositionSummary((dateCurrencyMap != null) ? dateCurrencyMap.getMainCurrency() : null, key,
            currencyPrecisionMap, transaction.getIdSecurityaccount()));
  }

  /**
   * Initializes transaction calculation parameters including split factors and exchange rates.
   * 
   * <p>
   * This method performs the preliminary calculations needed for transaction processing, including: determining
   * appropriate split factors for corporate actions, calculating exchange rates for currency conversions, and setting
   * up the calculation context for the specific transaction type.
   * </p>
   * 
   * <h4>Split Factor Calculation:</h4>
   * <p>
   * The method calculates split factors to ensure historical transactions remain accurate after corporate actions. For
   * margin instruments with connected transactions, it calculates the split factor from the original opening
   * transaction to maintain position integrity.
   * </p>
   * 
   * <h4>Exchange Rate Handling:</h4>
   * <p>
   * Determines the appropriate exchange rate for currency conversion based on transaction date, security currency, cash
   * account currency, and the portfolio's main currency.
   * </p>
   * 
   * @param transaction             the transaction being processed
   * @param securityPositionSummary the position summary being updated
   * @param securitysplitMap        map of security splits for corporate action adjustments
   * @param dateCurrencyMap         currency exchange rate data and main currency information
   * @param openMarginTransaction   the original opening transaction for margin position closures, or {@code null}
   * @return calculation parameters object containing exchange rates, split factors, and adjusted values
   */
  protected CalcTransactionPos initTransactionCalcTransCost(final Transaction transaction,
      final SecurityPositionSummary securityPositionSummary, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, Transaction openMarginTransaction) {
    SplitFactorAfterBefore splitToOpenTransaction = null;
    final SplitFactorAfterBefore splitFactorAfterBefore = Securitysplit.calcSplitFatorForFromDateAndToDate(
        transaction.getSecurity().getIdSecuritycurrency(), transaction.getTransactionTime(),
        dateCurrencyMap == null ? null : dateCurrencyMap.getUntilDate(), securitysplitMap);
    if (openMarginTransaction != null) {
      splitToOpenTransaction = Securitysplit.calcSplitFatorForFromDateAndToDate(
          transaction.getSecurity().getIdSecuritycurrency(), openMarginTransaction.getTransactionTime(),
          dateCurrencyMap == null ? null : transaction.getTransactionTime(), securitysplitMap);
      transaction.setSplitFactorFromBaseTransaction(splitToOpenTransaction.fromToDateFactor);
      if (dateCurrencyMap != null
          && DateHelper.isSameDay(transaction.getTransactionTime(), dateCurrencyMap.getUntilDate())) {
        securityPositionSummary.splitFactorFromBaseTransaction = splitToOpenTransaction.fromToDateFactor;
      }
    } else {
      securityPositionSummary.splitFactorFromBaseTransaction = splitFactorAfterBefore.fromToDateFactor;
    }
    return createCalcTransactionPos(transaction, securityPositionSummary, dateCurrencyMap, openMarginTransaction,
        splitFactorAfterBefore, splitToOpenTransaction);
  }

  /**
   * Creates and populates a calculation context object for transaction processing.
   * 
   * <p>
   * This private method consolidates all the calculation parameters needed for processing a transaction, including
   * exchange rates, split factors, costs, and adjusted units. It handles different transaction types and margin
   * instrument scenarios.
   * </p>
   * 
   * <h4>Transaction Cost Handling:</h4>
   * <p>
   * The method processes transaction costs and taxes only for specific transaction types that involve actual trading or
   * dividend payments. Administrative transactions are excluded from cost calculations.
   * </p>
   * 
   * <h4>Units Adjustment:</h4>
   * <p>
   * Calculates split-adjusted units based on corporate actions that occurred after the transaction date. For margin
   * instruments, applies directional adjustments based on whether the transaction is a position reduction or closure.
   * </p>
   * 
   * @param transaction             the transaction being processed
   * @param securityPositionSummary the position summary being updated
   * @param dateCurrencyMap         currency exchange rate information
   * @param openMarginTransaction   the original margin transaction for closures, or {@code null}
   * @param splitFactorAfterBefore  split factors for the transaction period
   * @param splitToOpenTransaction  split factors from margin opening to current transaction
   * @return calculation context object with all necessary parameters
   */
  private CalcTransactionPos createCalcTransactionPos(final Transaction transaction,
      final SecurityPositionSummary securityPositionSummary, final DateTransactionCurrencypairMap dateCurrencyMap,
      Transaction openMarginTransaction, SplitFactorAfterBefore splitFactorAfterBefore,
      final SplitFactorAfterBefore splitToOpenTransaction) {
    final CalcTransactionPos ctp = new CalcTransactionPos(
        DataBusinessHelper.getCurrencyExchangeRateToMainCurreny(transaction, dateCurrencyMap),
        splitToOpenTransaction == null ? splitFactorAfterBefore.fromToDateFactor
            : splitToOpenTransaction.fromToDateFactor);

    if (transaction.getTransactionType() == TransactionType.ACCUMULATE
        || transaction.getTransactionType() == TransactionType.REDUCE
        || transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL
        || transaction.getTransactionType() == TransactionType.DIVIDEND) {
      ctp.securitiesNetPrice = openMarginTransaction == null ? transaction.getSeucritiesNetPrice()
          : transaction.getSeucritiesNetPrice(openMarginTransaction.getQuotation());
      ctp.transactionCost = (transaction.getTransactionCost() != null) ? transaction.getTransactionCost() : 0.0;
      ctp.taxCost = (transaction.getTaxCost() != null) ? transaction.getTaxCost() : 0.0;
      securityPositionSummary.transactionCost += ctp.transactionCost;
      securityPositionSummary.taxCost += ctp.taxCost;
      ctp.transactionTaxCost = ctp.taxCost + ctp.transactionCost;
    }
    securityPositionSummary.transactionGainLoss = null;
    securityPositionSummary.transactionGainLossPercentage = null;
    securityPositionSummary.transactionGainLossMC = null;
    securityPositionSummary.transactionCurrencyGainLossMC = null;
    securityPositionSummary.transactionExchangeRate = ctp.exchangeRate;

    ctp.unitsSplited = transaction.getUnits() * splitFactorAfterBefore.fromToDateFactor;
    securityPositionSummary.closePriceFactor = splitFactorAfterBefore.toDateUntilNow;
    if (transaction.isMarginInstrument() && (transaction.getTransactionType() == TransactionType.REDUCE
        || transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL)) {
      ctp.unitsSplited *= -1.0;
    }
    return ctp;
  }

  /**
   * Creates a hypothetical buy or sell transaction for position analysis.
   * 
   * <p>
   * This utility method generates simulated transactions that can be used for what-if analysis, portfolio valuation,
   * and position closure simulations. The created transaction contains realistic values based on current market
   * conditions and position details.
   * </p>
   * 
   * <h4>Transaction Properties:</h4>
   * <ul>
   * <li><strong>Negative ID:</strong> Uses negative transaction IDs to distinguish from real transactions</li>
   * <li><strong>Current Date:</strong> Uses the current system time as the transaction time</li>
   * <li><strong>Market Price:</strong> Uses the provided last price for valuation</li>
   * <li><strong>Currency Matching:</strong> Creates cash account in security's currency</li>
   * </ul>
   * 
   * <h4>Calculated Values:</h4>
   * <p>
   * The cash account amount is calculated as the product of units and last price, representing the gross proceeds (for
   * sells) or cost (for buys) before considering transaction costs or taxes.
   * </p>
   * 
   * @param transactionType         the type of hypothetical transaction ({@link TransactionType#HYPOTHETICAL_BUY} or
   *                                {@link TransactionType#HYPOTHETICAL_SELL})
   * @param securityPositionSummary the position summary containing units and security information
   * @param lastPrice               the current market price to use for the transaction
   * @param negativeIdNumberCreater utility for generating unique negative transaction IDs
   * @return a fully configured hypothetical transaction ready for calculation processing
   */
  protected Transaction createHypotheticalBuySellTransaction(final TransactionType transactionType,
      final SecurityPositionSummary securityPositionSummary, final double lastPrice,
      NegativeIdNumberCreater negativeIdNumberCreater) {
    final Transaction transaction = new Transaction(null, lastPrice * securityPositionSummary.units, transactionType,
        new Date());
    transaction.setIdTransaction(negativeIdNumberCreater.getNegativeId());
    final Cashaccount cashaccount = new Cashaccount();
    cashaccount.setCurrency(securityPositionSummary.getSecurity().getCurrency());
    transaction.setCashaccount(cashaccount);
    transaction.setSecuritycurrency(securityPositionSummary.getSecurity());
    transaction.setQuotation(lastPrice);
    return transaction;
  }

  /**
  * Calculation context object that encapsulates all parameters needed for processing a single transaction.
  * 
  * <p>
  * This static inner class serves as a data transfer object that consolidates various calculation
  * parameters including exchange rates, prices, costs, and split factors. It provides a clean
  * interface for passing complex calculation state between methods.
  * </p>
  */
  protected static class CalcTransactionPos {
    /** 
     * Exchange rate from transaction currency to main currency, or {@code null} if no conversion needed.
     * This rate is used for converting all transaction amounts to the portfolio's main currency.
     */
    public final Double exchangeRate;
    
    /** 
     * Net price of securities involved in the transaction, calculated as units × price.
     * For margin instruments, this may be adjusted based on the original opening position.
     */
    public double securitiesNetPrice = 0.0;

    /**
     * Combined transaction and tax costs for the transaction. This represents the total additional costs beyond the
     * security price.
     */
    public double transactionTaxCost = 0.0;

    /**
     * Tax costs associated with the transaction, such as withholding taxes on dividends. May be zero for transactions
     * that don't incur tax obligations.
     */
    public double taxCost = 0.0;

    /**
     * Transaction costs such as brokerage fees, commissions, and other trading charges. These costs are typically
     * deducted from gains or added to the cost basis.
     */
    public double transactionCost = 0.0;

    /**
     * Number of units adjusted for all security splits that occurred after the transaction date. This ensures
     * consistent unit calculations across corporate actions.
     */
    public double unitsSplited;

    
    public double splitFactorFromBaseTransaction;

    public CalcTransactionPos(Double exchangeRate, double splitFactorFromBaseTransaction) {
      this.exchangeRate = exchangeRate;
      this.splitFactorFromBaseTransaction = splitFactorFromBaseTransaction;
    }

    /**
     * Calculates the real (pre-split) number of units for the transaction.
     * 
     * <p>
     * This method reverses the split adjustment to determine the original number of units
     * as they existed at the time of the transaction, before any subsequent corporate actions.
     * This is useful for historical analysis and position reconciliation.
     * </p>
     * 
     * @return the original number of units before split adjustments
     */
    public double getRealUntis() {
      return unitsSplited / splitFactorFromBaseTransaction;
    }

    @Override
    public String toString() {
      return "CalcTransactionPos [exchangeRate=" + exchangeRate + ", securitiesNetPrice=" + securitiesNetPrice
          + ", transactionTaxCost=" + transactionTaxCost + ", taxCost=" + taxCost + ", transactionCost="
          + transactionCost + ", unitsSplited=" + unitsSplited + ", splitFactorFromBaseTransaction="
          + splitFactorFromBaseTransaction + "]";
    }

  }
}
