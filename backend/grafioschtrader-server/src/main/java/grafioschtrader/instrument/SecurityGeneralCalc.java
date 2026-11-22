package grafioschtrader.instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.CostLedger;
import grafioschtrader.reportviews.securityaccount.CostLot;
import grafioschtrader.reportviews.securityaccount.DisposalEstimate;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.TransactionType;

/**
 * Calculator for standard investment securities. The cost of a sale follows the cost basis method of its security
 * account.
 *
 * <p>
 * This class implements position tracking and gain/loss calculations for traditional investment instruments including
 * stocks, bonds, ETFs, mutual funds, and money market securities. Unlike margin instruments, whose close refers to its
 * own opening, a sale of a standard security has to be told which purchases it consumes.
 * </p>
 *
 * <h3>Cost Basis Methods:</h3>
 * <ul>
 * <li><strong>Average cost:</strong> The purchases of all security accounts on this method form one pool per position
 * of the report, a sale takes its proportional share of the pooled cost</li>
 * <li><strong>FIFO and LIFO:</strong> Every security account keeps its own lots in a
 * {@link grafioschtrader.reportviews.securityaccount.CostLedger}, a sale consumes the oldest or the newest of them.
 * Two security accounts never share lots, so a sale shows the same gain in every view</li>
 * </ul>
 * <p>
 * The method only moves gain between realized and unrealized. Realized gain plus market value less remaining cost
 * equals proceeds plus market value less purchase costs, which no method can change. The hypothetical sale disposes of
 * the whole position and therefore costs the whole remaining cost under every method.
 * </p>
 *
 * <h3>Supported Instrument Types:</h3>
 * <dl>
 * <dt><strong>Equities (Stocks)</strong></dt>
 * <dd>Standard stock trading with dividend processing and corporate action adjustments. Supports both domestic and
 * international equities with currency conversion.</dd>
 *
 * <dt><strong>Fixed Income (Bonds)</strong></dt>
 * <dd>Bond trading with specialized accrued interest handling. Automatically separates accrued interest from principal
 * transactions for accurate yield-to-maturity calculations and tax reporting.</dd>
 *
 * <dt><strong>Convertible Bonds</strong></dt>
 * <dd>Hybrid securities combining bond and equity characteristics. Handles both interest payments and potential
 * conversion features with appropriate accrued interest calculations.</dd>
 *
 * <dt><strong>Money Market Securities</strong></dt>
 * <dd>Short-term debt instruments with special pricing considerations. For securities without external price feeds, the
 * system uses transaction prices as closing prices for valuation consistency.</dd>
 *
 * <dt><strong>Investment Funds (ETFs, Mutual Funds)</strong></dt>
 * <dd>Pooled investment vehicles with dividend distributions and potential capital gains distributions. Handles both
 * accumulating and distributing fund types.</dd>
 * </dl>
 *
 * <h3>Performance Calculation:</h3>
 * <p>
 * Gain/loss calculations consider the full total return including:
 * </p>
 * <ul>
 * <li>Capital appreciation/depreciation based on the cost basis method of the security account</li>
 * <li>Dividend and interest income (gross or net of taxes)</li>
 * <li>Currency exchange gains/losses for foreign securities</li>
 * <li>Transaction costs and tax implications</li>
 * </ul>
 */
public class SecurityGeneralCalc extends SecurityBaseCalc {

  private static final Logger log = LoggerFactory.getLogger(SecurityGeneralCalc.class);

  /**
   * Processes a single transaction for standard investment securities.
   *
   * <p>
   * This method handles the complete transaction lifecycle for traditional investment instruments, applying the cost
   * basis method of its security account to maintain accurate position tracking. The calculation considers corporate
   * actions, currency conversions, and instrument-specific features such as accrued interest for bonds.
   * </p>
   *
   * @param transaction             the transaction to process, containing trade details, pricing, and costs
   * @param summarySecurityMap      map of existing position summaries, keyed by security, updated with results
   * @param securitysplitMap        map of security splits by security ID for corporate action adjustments
   * @param excludeDivTaxcost       if {@code true}, excludes dividend withholding taxes from gain/loss calculations
   * @param dateCurrencyMap         currency exchange rate data for multi-currency portfolio calculations
   * @param negativeIdNumberCreater utility for generating unique IDs for derived transactions (e.g., accrued interest)
   * @param currencyPrecisionMap    precision settings for different currencies to ensure accurate rounding
   */
  @Override
  void calcSingleSecurityTransaction(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater,
      Map<String, Integer> currencyPrecisionMap, CostBasisMethods costBasisMethods) {

    SecurityPositionSummary securityPositionSummary = getSecurityPositionSummary(transaction, summarySecurityMap,
        dateCurrencyMap, currencyPrecisionMap);
    calcTransactionPosition(transaction, securityPositionSummary, excludeDivTaxcost, securitysplitMap, true,
        dateCurrencyMap, negativeIdNumberCreater, costBasisMethods);
  }

  /**
   * Creates a hypothetical sell transaction to simulate complete position liquidation for valuation purposes.
   *
   * <p>
   * Unlike margin instruments that may have multiple open positions, standard securities maintain a single pooled
   * position that can be entirely liquidated with one hypothetical transaction. This method calculates the unrealized
   * gain/loss that would result from selling the entire position at the current market price.
   * </p>
   *
   * <h4>Simulation Process:</h4>
   * <ol>
   * <li><strong>State Preservation:</strong> Saves current unit count to restore after simulation</li>
   * <li><strong>Transaction Creation:</strong> Generates a hypothetical sell for all units at current price</li>
   * <li><strong>Gain/Loss Calculation:</strong> Computes unrealized performance on the whole remaining cost</li>
   * <li><strong>State Restoration:</strong> Returns position to original state while preserving valuation data</li>
   * <li><strong>Summary Recording:</strong> Adds transaction details to summary for reporting</li>
   * </ol>
   *
   * @param securityPositionSummary    the current position to simulate selling
   * @param lastPrice                  the current market price for the hypothetical sale
   * @param securitysplitMap           map of security splits for corporate action adjustments
   * @param dateCurrencyMap            currency exchange rate data for multi-currency calculations
   * @param securityTransactionSummary container for the hypothetical transaction details, or null if not needed
   * @param negativeIdNumberCreater    utility for generating unique negative IDs for hypothetical transactions
   */
  @Override
  void createHypotheticalSellTransaction(SecurityPositionSummary securityPositionSummary, double lastPrice,
      Map<Integer, List<Securitysplit>> securitysplitMap, DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityTransactionSummary securityTransactionSummary, NegativeIdNumberCreater negativeIdNumberCreater) {
    createHypotheticalSellTransaction(securityPositionSummary, lastPrice, securitysplitMap, dateCurrencyMap,
        securityTransactionSummary, negativeIdNumberCreater, null);
  }

  /**
   * Like
   * {@link #createHypotheticalSellTransaction(SecurityPositionSummary, double, Map, DateTransactionCurrencypairMap, SecurityTransactionSummary, NegativeIdNumberCreater)},
   * but the sale carries the estimated commission and tax, so its gain or loss is the one after these costs. The cash
   * account amount is reduced by them accordingly.
   *
   * @param disposalEstimate the estimated costs of the sale in security currency, null for a sale without costs
   */
  void createHypotheticalSellTransaction(SecurityPositionSummary securityPositionSummary, double lastPrice,
      Map<Integer, List<Securitysplit>> securitysplitMap, DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityTransactionSummary securityTransactionSummary, NegativeIdNumberCreater negativeIdNumberCreater,
      DisposalEstimate disposalEstimate) {

    if (securityPositionSummary.units != 0.0) {
      final double beforeUnits = securityPositionSummary.units;
      Transaction transaction = createHypotheticalBuySellTransaction(TransactionType.HYPOTHETICAL_SELL,
          securityPositionSummary, lastPrice, negativeIdNumberCreater);

      transaction.setUnits(securityPositionSummary.units);
      if (disposalEstimate != null) {
        // Rounded like a booked cost, so the sale shows and deducts the amounts the currency can actually carry
        Double commission = disposalEstimate.commission() == null ? null
            : DataHelper.round(disposalEstimate.commission(), securityPositionSummary.precision);
        Double tax = disposalEstimate.tax() == null ? null
            : DataHelper.round(disposalEstimate.tax(), securityPositionSummary.precision);
        transaction.setTransactionCost(commission);
        transaction.setTaxCost(tax);
        transaction.setCashaccountAmount(
            transaction.getCashaccountAmount() - (commission == null ? 0.0 : commission) - (tax == null ? 0.0 : tax));
      }
      // A hypothetical sale disposes of the whole position and resolves no method, the constant is never read
      calcTransactionPosition(transaction, securityPositionSummary, false, securitysplitMap, false, dateCurrencyMap,
          negativeIdNumberCreater, CostBasisMethods.AVERAGE_ONLY);
      securityPositionSummary.units = beforeUnits;
      securityPositionSummary.valueSecurity = beforeUnits * lastPrice;
      if (securityTransactionSummary != null) {
        if (disposalEstimate == null) {
          securityTransactionSummary.createAndAddPositionGainLoss(transaction);
        } else {
          securityTransactionSummary.createAndAddPositionGainLoss(transaction, disposalEstimate);
        }
      }
    }
  }

  /**
   * Processes a transaction and integrates the results into both position and transaction summaries.
   *
   * <p>
   * This method serves as the integration point between transaction processing and summary reporting, ensuring that
   * both cumulative position metrics and individual transaction performance data are properly maintained for
   * comprehensive portfolio analysis and audit trails.
   * </p>
   *
   * @param transaction                the transaction to process and record
   * @param securityTransactionSummary summary container tracking both position and transaction details
   * @param excludeDivTaxcost          whether to exclude dividend taxes from gain/loss calculations
   * @param securitySplitMap           map of security splits for corporate action handling
   * @param dateCurrencyMap            currency exchange rate data for multi-currency support
   * @param negativeIdNumberCreater    utility for generating IDs for derived transactions
   */
  @Override
  void calcTransactionAndAddToPosition(final Transaction transaction,
      final SecurityTransactionSummary securityTransactionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitySplitMap, final DateTransactionCurrencypairMap dateCurrencyMap,
      NegativeIdNumberCreater negativeIdNumberCreater, CostBasisMethods costBasisMethods) {
    calcTransactionPosition(transaction, securityTransactionSummary.securityPositionSummary, excludeDivTaxcost,
        securitySplitMap, false, dateCurrencyMap, negativeIdNumberCreater, costBasisMethods);
    securityTransactionSummary.createAndAddPositionGainLoss(transaction);
  }

  /**
   * Creates and processes accrued interest transactions for bonds and convertible bonds.
   *
   * <p>
   * When trading bonds, the purchase or sale price typically includes accrued interest that has accumulated since the
   * last coupon payment. This method automatically separates this accrued interest component into a distinct
   * transaction for accurate accounting, tax reporting, and yield calculations.
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
   * @param security                           the security being traded (must be bond or convertible bond)
   * @param requiredTransactionTypeTransaction the transaction type that should trigger accrued interest processing
   * @param transaction                        the original transaction containing accrued interest data
   * @param securityTransactionSummary         summary container to receive the accrued interest transaction
   * @param excludeDivTaxcost                  whether to exclude taxes from the accrued interest calculation
   * @param securitySplitMap                   map of security splits for corporate action adjustments
   * @param dateCurrencyMap                    currency exchange rate data for multi-currency calculations
   * @param negativeIdNumberCreater            utility for generating unique IDs for the accrued interest transaction
   */
  void createAccruedInterestPostion(final Security security, final TransactionType requiredTransactionTypeTransaction,
      final Transaction transaction, final SecurityTransactionSummary securityTransactionSummary,
      final boolean excludeDivTaxcost, final Map<Integer, List<Securitysplit>> securitySplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater,
      CostBasisMethods costBasisMethods) {
    if (security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
        || security.getAssetClass().getCategoryType() == AssetclassType.CONVERTIBLE_BOND) {

      if (transaction.getTransactionType() == requiredTransactionTypeTransaction
          && transaction.getAssetInvestmentValue1() != null && transaction.getAssetInvestmentValue1() != 0.0) {
        final Transaction accruedInterestTransaction = createAccruedInterestTransaction(transaction,
            negativeIdNumberCreater);
        calcTransactionAndAddToPosition(accruedInterestTransaction, securityTransactionSummary, excludeDivTaxcost,
            securitySplitMap, dateCurrencyMap, negativeIdNumberCreater, costBasisMethods);
      }
    }
  }

  /**
   * Creates a separate transaction to represent the accrued interest component of a bond trade.
   *
   * <p>
   * This method constructs a specialized transaction that isolates the accrued interest portion from the principal
   * component of a bond transaction. The resulting transaction has characteristics appropriate for interest income
   * recognition and can be processed through the normal calculation pipeline while maintaining proper accounting
   * separation.
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
   * @param transaction             the original bond transaction containing accrued interest data
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
    // Accrued interest is settled together with the bond trade, so it is exposed to exactly the same currencies. The
    // three fields below are what DataBusinessHelper.getCurrencyExchangeRateToMainCurreny reads; without them it
    // returned null for every synthetic row, so the accrued interest of a foreign bond carried no exchange rate at
    // all: no transactionGainLossMC, and no contribution to the currency attribution although the money did move.
    accruedInterestTransaction.setCashaccount(transaction.getCashaccount());
    accruedInterestTransaction.setIdCurrencypair(transaction.getIdCurrencypair());
    accruedInterestTransaction.setCurrencyExRate(transaction.getCurrencyExRate());
    return accruedInterestTransaction;
  }

  /**
   * Core calculation method that processes all transaction types for standard securities.
   *
   * <p>
   * This method implements the central calculation logic for standard investment securities and dispatches a
   * transaction to its purchase, sale or income calculation. It handles the complexity of multi-currency
   * calculations, corporate action adjustments, and instrument-specific features. Which purchases a sale consumes is
   * decided by the cost basis method of its security account.
   * </p>
   *
   * @param transaction             the transaction to process
   * @param securityPositionSummary the position summary to update with transaction effects
   * @param excludeDivTaxcost       whether to exclude dividend withholding taxes from calculations
   * @param securitysplitMap        map of security splits for corporate action adjustments
   * @param simulateAccruedRecords  whether to create simulated accrued interest transactions for bonds
   * @param dateCurrencyMap         currency exchange rate data for multi-currency calculations
   * @param negativeIdNumberCreater utility for generating IDs for derived transactions
   */
  private void calcTransactionPosition(final Transaction transaction,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean simulateAccruedRecords,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater,
      CostBasisMethods costBasisMethods) {

    final CalcTransactionPos ctp = initTransactionCalcTransCost(transaction, securityPositionSummary, securitysplitMap,
        dateCurrencyMap, null);

    switch (transaction.getTransactionType()) {
    case ACCUMULATE:
      accumulate(transaction, ctp, securityPositionSummary, excludeDivTaxcost, simulateAccruedRecords,
          negativeIdNumberCreater, costBasisMethods);
      break;
    case REDUCE:
    case HYPOTHETICAL_SELL:
      reduceOrHypotheticalSell(transaction, ctp, securityPositionSummary, excludeDivTaxcost, simulateAccruedRecords,
          negativeIdNumberCreater, costBasisMethods);
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

  /** Moves the cost basis of a spin-off while recording the market-value flows of its bookings. */
  void calcSpinOffPosition(Transaction transaction, SecurityPositionSummary position,
      Map<Integer, List<Securitysplit>> splits, boolean excludeDivTax, DateTransactionCurrencypairMap currencyMap,
      CostBasisMethods methods, SpinOffContext spinOff, boolean useParentPosition) {
    CalcTransactionPos ctp = initTransactionCalcTransCost(transaction, position, splits, currencyMap, null);
    var role = transaction.getSecurityActionRole();
    var method = methods.forAccount(transaction.getIdSecurityaccount());
    switch (role) {
    case SPIN_OFF_SALE -> {
      var allocation = spinOff.allocation(transaction, useParentPosition ? position : null, excludeDivTax, currencyMap,
          methods, ctp.unitsSplited);
      List<CostLot> before = copyLots(position.getCostLedger(transaction.getIdSecurityaccount()));
      position.removeSpinOffCost(transaction.getIdSecurityaccount(), allocation.allocatedCost(),
          allocation.parentLotsAfter());
      position.transactionCostBase = allocation.allocatedCost();
      position.transactionSpinOffLots = movedParts(before,
          position.getCostLedger(transaction.getIdSecurityaccount()));
    }
    case SPIN_OFF_REPURCHASE -> {
      // The sale and repurchase are movements of market value. The parent's units stay in their existing lots.
    }
    case SPIN_OFF_CHILD -> {
      var allocation = spinOff.allocation(transaction, null, excludeDivTax, currencyMap, methods, ctp.unitsSplited);
      position.adjustedCostBase += allocation.childCost();
      position.units += ctp.unitsSplited;
      position.costBasisMethods.add(method);
      if (method != CostBasisMethod.AVERAGE_COST) {
        position.addSpinOffLots(transaction.getIdSecurityaccount(), allocation.childLots());
        position.transactionSpinOffLots = allocation.childLots();
      } else {
        position.transactionSpinOffLots = List.of();
      }
      position.transactionCostBase = allocation.childCost();
      position.transactionCostBasisIncomplete = allocation.incomplete() ? Boolean.TRUE : null;
    }
    }
    addFlowToCurrencyPosition(transaction, ctp, position,
        role == grafioschtrader.types.SecurityActionRole.SPIN_OFF_SALE ? -ctp.securitiesNetPrice
            : ctp.securitiesNetPrice);
    position.roundUnits();
  }

  /** Copies of the open lots of a ledger, with their cost before a spin-off changes them; empty without a ledger. */
  private static List<CostLot> copyLots(CostLedger ledger) {
    return ledger == null ? List.of()
        : ledger.getLots().stream().map(lot -> lot.withAmount(lot.units, lot.cost)).toList();
  }

  /**
   * The cost each parent lot handed to the child: the lot with its units and the difference between its cost before and
   * after the spin-off. The ledger keeps its order and identities, so both lists are aligned by position.
   */
  private static List<CostLot> movedParts(List<CostLot> before, CostLedger ledgerAfter) {
    if (before.isEmpty() || ledgerAfter == null) {
      return List.of();
    }
    List<CostLot> after = ledgerAfter.getLots();
    List<CostLot> moved = new ArrayList<>(before.size());
    for (int i = 0; i < before.size() && i < after.size(); i++) {
      CostLot lot = before.get(i);
      moved.add(lot.withAmount(lot.units, lot.cost - after.get(i).cost));
    }
    return moved;
  }

  /**
   * Processes purchase (accumulate) transactions.
   *
   * <p>
   * A purchase adds its cost and its units to the totals of the position and updates all relevant position metrics
   * including multi-currency tracking and instrument-specific features. Under FIFO and LIFO it additionally becomes a
   * lot in the ledger of its security account; under average cost it joins the pool of the position.
   * </p>
   *
   * <h4>Average Cost Calculation:</h4>
   * <p>
   * The average cost of the pool is:
   * <code>(Previous Cost Basis + New Purchase Cost) / (Previous Units + New Units)</code>
   * </p>
   *
   * <h4>Processing Steps:</h4>
   * <ol>
   * <li><strong>Cost Basis Update:</strong> Adds transaction cost (price × units + fees + taxes) to total cost
   * basis</li>
   * <li><strong>Units Update:</strong> Adds split-adjusted units to total position</li>
   * <li><strong>Price Setting:</strong> For money market securities without price feeds, uses transaction price</li>
   * <li><strong>Accrued Interest:</strong> Simulates accrued interest transactions for bonds if enabled</li>
   * <li><strong>Currency Tracking:</strong> Updates foreign currency balances and main currency equivalents</li>
   * </ol>
   *
   * <h4>Money Market Special Handling:</h4>
   * <p>
   * For money market securities that lack external price feeds, the transaction price is used as the closing price to
   * ensure accurate position valuation in subsequent calculations.
   * </p>
   *
   * @param transaction             the purchase transaction to process
   * @param ctp                     calculation context with exchange rates, split factors, and adjusted values
   * @param securityPositionSummary the position summary to update
   * @param excludeDivTaxcost       whether to exclude dividend taxes (not applicable to purchases)
   * @param simulateAccruedRecords  whether to create accrued interest transactions for bonds
   * @param negativeIdNumberCreater utility for generating IDs for simulated transactions
   */
  private void accumulate(final Transaction transaction, final CalcTransactionPos ctp,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost,
      final boolean simulateAccruedRecords, final NegativeIdNumberCreater negativeIdNumberCreater,
      final CostBasisMethods costBasisMethods) {
    final Security security = transaction.getSecurity();
    final double cost = ctp.securitiesNetPrice + ctp.transactionTaxCost;
    securityPositionSummary.adjustedCostBase += cost;
    securityPositionSummary.units += ctp.unitsSplited;
    final CostBasisMethod method = costBasisMethods.forAccount(transaction.getIdSecurityaccount());
    securityPositionSummary.costBasisMethods.add(method);
    if (method != CostBasisMethod.AVERAGE_COST) {
      securityPositionSummary.addToLedger(transaction, ctp.unitsSplited, cost,
          hasCurrencyExposure(transaction, ctp, securityPositionSummary) ? ctp.exchangeRate : null);
    }

    if (security.getAssetClass().getCategoryType() == AssetclassType.MONEY_MARKET
        && (security.getIdConnectorHistory() == null || security.getIdConnectorHistory().isEmpty())
        && (security.getIdConnectorIntra() == null || security.getIdConnectorIntra().isEmpty())) {
      securityPositionSummary.closePrice = transaction.getQuotation();
    }
    if (simulateAccruedRecords) {
      simulateAccruedInterest(transaction, securityPositionSummary, excludeDivTaxcost, ctp.exchangeRate,
          negativeIdNumberCreater);
    }
    addFlowToCurrencyPosition(transaction, ctp, securityPositionSummary,
        ctp.securitiesNetPrice + ctp.transactionTaxCost);
  }

  /**
   * Books one cash flow of the position into the two counters behind the currency attribution.
   *
   * <p>
   * The sign convention is that of the position, not of the cash account: money going into the security is positive,
   * everything coming back out — sale proceeds, dividends, accrued interest — is negative. The main currency
   * counterpart uses the rate of the transaction date, so the difference against the reporting date rate is exactly the
   * currency result. See {@code specification/Currency_Gain_Loss_Attribution.md}.
   * </p>
   *
   * <p>
   * Two flows are deliberately not booked. A security already denominated in the main currency has no currency result,
   * and {@code DataBusinessHelper.getCurrencyExchangeRateToMainCurreny} would hand out the cash account cross rate for
   * it — 85.65 for a CHF instrument paying into a JPY account — which would invent tens of thousands of francs of gain.
   * And a hypothetical sale is not a cash flow at all; because it would be booked at the reporting date rate its two
   * contributions cancel, so leaving it out is both simpler and free of the {@code getSLast()} rate it would otherwise
   * drag in.
   * </p>
   *
   * @param transaction             the transaction being processed
   * @param ctp                     calculation context holding the transaction date exchange rate
   * @param securityPositionSummary the position whose counters are updated
   * @param flowSC                  the flow in the security currency, positive into the position
   */
  private void addFlowToCurrencyPosition(final Transaction transaction, final CalcTransactionPos ctp,
      final SecurityPositionSummary securityPositionSummary, final double flowSC) {
    if (transaction.getTransactionType() != TransactionType.HYPOTHETICAL_SELL) {
      // Kept for every currency: the period performance per security converts it with its own closing rates
      securityPositionSummary.positionFlow += flowSC;
    }
    if (!hasCurrencyExposure(transaction, ctp, securityPositionSummary)) {
      // Leave transactionFlowSC null: a flow that carries no currency exposure must not be given a per-transaction
      // share either, or the shares stop adding up to the position total.
      return;
    }
    securityPositionSummary.transactionFlowSC = flowSC;
    securityPositionSummary.netInvestedSC += flowSC;
    securityPositionSummary.netInvestedMC += flowSC * ctp.exchangeRate;
  }

  /**
   * Processes sale (reduce) and hypothetical sale transactions.
   *
   * <p>
   * A sale realizes the difference between its proceeds and the cost of the units sold, including the currency
   * gain/loss attribution for foreign securities. The cost comes from the cost basis method of the security account
   * of the sale: the proportional share of the average pool, or the lots FIFO or LIFO consume from the ledger of that
   * security account. The hypothetical sale disposes of the whole position and takes the whole remaining cost.
   * </p>
   *
   * <h4>Cost Allocation:</h4>
   * <p>
   * Average cost and the hypothetical sale allocate <code>(Units Sold / Units) × Cost Basis</code> of their pool.
   * FIFO and LIFO allocate the cost of the consumed lots, a partly consumed lot gives its proportional share.
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
   * @param transaction             the sale transaction to process
   * @param ctp                     calculation context with exchange rates, split factors, and adjusted values
   * @param securityPositionSummary the position summary to update
   * @param excludeDivTaxcost       whether to exclude dividend taxes from gain/loss calculations
   * @param simulateAccruedRecords  whether to create accrued interest transactions for bonds
   * @param negativeIdNumberCreater utility for generating IDs for simulated transactions
   */
  /**
   * Whether a flow of this transaction is exposed to the exchange rate between the security currency and the main
   * currency. The exchange rate of the transaction alone does not tell: for a security in the main currency it is the
   * cross rate of the cash account.
   */
  private boolean hasCurrencyExposure(final Transaction transaction, final CalcTransactionPos ctp,
      final SecurityPositionSummary securityPositionSummary) {
    return ctp.exchangeRate != null && transaction.getTransactionType() != TransactionType.HYPOTHETICAL_SELL
        && !transaction.getSecurity().getCurrency().equals(securityPositionSummary.mainCurrency);
  }

  private void reduceOrHypotheticalSell(final Transaction transaction, final CalcTransactionPos ctp,
      final SecurityPositionSummary securityPositionSummary, final boolean excludeDivTaxcost,
      final boolean simulateAccruedRecords, final NegativeIdNumberCreater negativeIdNumberCreater,
      final CostBasisMethods costBasisMethods) {
    if (simulateAccruedRecords) {
      simulateAccruedInterest(transaction, securityPositionSummary, excludeDivTaxcost, ctp.exchangeRate,
          negativeIdNumberCreater);
    }
    final double balance = ctp.securitiesNetPrice - ctp.transactionTaxCost;
    final double acb = costBaseOfSale(transaction, ctp, securityPositionSummary, balance, costBasisMethods);
    securityPositionSummary.transactionGainLoss = balance - acb;
    securityPositionSummary.transactionCostBase = acb;
    // Without a cost there is no percentage: a lot received for free can cover a FIFO or LIFO sale on its own. An
    // uncovered sale has a cost that is partly a substitute, so its percentage would mislead.
    securityPositionSummary.transactionGainLossPercentage = acb == 0.0
        || securityPositionSummary.transactionCostBasisIncomplete != null ? null
            : securityPositionSummary.transactionGainLoss / acb * 100.0;
    securityPositionSummary.adjustedCostBase -= acb;
    securityPositionSummary.units -= ctp.unitsSplited;
    securityPositionSummary.gainLossSecurity += securityPositionSummary.transactionGainLoss;
    if (transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL) {
      // The sale that only values the open position has realized nothing
      securityPositionSummary.unrealizedGainLossSecurity += securityPositionSummary.transactionGainLoss;
    }
    if (ctp.exchangeRate != null) {
      securityPositionSummary.transactionGainLossMC = securityPositionSummary.transactionGainLoss * ctp.exchangeRate;
    }
    addFlowToCurrencyPosition(transaction, ctp, securityPositionSummary, -balance);
  }

  /**
   * Simulates accrued interest transactions for bond trades during calculation processing.
   *
   * <p>
   * This method creates and processes simulated accrued interest transactions for bonds and convertible bonds during
   * the calculation phase. Unlike the formal accrued interest position creation, this simulation is used for internal
   * calculation consistency and doesn't create persistent transaction records.
   * </p>
   *
   * <h4>Simulation Purpose:</h4>
   * <ul>
   * <li><strong>Calculation Consistency:</strong> Ensures accrued interest is properly handled in all calculation
   * paths</li>
   * <li><strong>Internal Processing:</strong> Maintains consistent logic between different calculation scenarios</li>
   * <li><strong>Position Accuracy:</strong> Improves accuracy of position calculations that involve accrued
   * interest</li>
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
   * @param transaction             the original transaction that may contain accrued interest
   * @param securityPositionSummary the position summary to update with simulated accrued interest
   * @param excludeDivTaxcost       whether to exclude taxes from the accrued interest calculation
   * @param exchangeRate            the exchange rate for currency conversion, or null if not applicable
   * @param negativeIdNumberCreater utility for generating IDs for simulated transactions
   */
  /**
   * The cost of the units of a sale in security currency. A booked sale takes it from the average cost pool or from
   * the lots of its security account, depending on the method of that account. A hypothetical sale always disposes of
   * the whole position, so its cost is the whole remaining cost whatever the methods are, also for a position whose
   * security accounts differ. It touches no ledger and resolves no method, because its transaction carries no security
   * account.
   */
  private double costBaseOfSale(final Transaction transaction, final CalcTransactionPos ctp,
      final SecurityPositionSummary securityPositionSummary, final double balance,
      final CostBasisMethods costBasisMethods) {
    if (transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL) {
      return ctp.unitsSplited * securityPositionSummary.adjustedCostBase / securityPositionSummary.units;
    }
    final CostBasisMethod method = costBasisMethods.forAccount(transaction.getIdSecurityaccount());
    securityPositionSummary.transactionCostBasisMethod = method;
    securityPositionSummary.costBasisMethods.add(method);
    if (method == CostBasisMethod.AVERAGE_COST) {
      return ctp.unitsSplited * securityPositionSummary.averageCost() / securityPositionSummary.averageUnits();
    }
    final double acb = securityPositionSummary.reduceFromLedger(transaction, ctp.unitsSplited, balance, method);
    if (securityPositionSummary.transactionCostBasisIncomplete != null) {
      log.warn("The purchases in security account {} do not cover the sale {} of security {}",
          transaction.getIdSecurityaccount(), transaction.getIdTransaction(),
          transaction.getSecurity().getIdSecuritycurrency());
    }
    return acb;
  }

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
   * This method processes income-generating transactions that provide cash flow to the investor without changing the
   * fundamental position structure. Unlike buy/sell transactions, dividend and interest payments don't affect the unit
   * count or the cost basis under any method, but they do contribute to total return and may have tax implications.
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
   * The method calculates percentage returns relative to the current cost basis, providing insights into income yield
   * relative to the investment's cost. For positions with zero cost basis, percentage calculation is skipped to avoid
   * division by zero.
   * </p>
   *
   * <h4>Multi-Currency Handling:</h4>
   * <p>
   * For foreign securities, dividend income is converted to the portfolio's main currency using the appropriate
   * exchange rate, enabling accurate total return calculations across multi-currency portfolios.
   * </p>
   *
   * @param transaction             the dividend or interest transaction to process
   * @param securityPositionSummary the position summary to update with income
   * @param excludeDivTaxcost       whether to exclude withholding taxes from the income calculation
   * @param transactionCost         any transaction costs associated with the income (typically zero)
   * @param taxCost                 withholding taxes or other tax costs associated with the income
   * @param exchangeRate            exchange rate for converting foreign currency income to main currency
   */
  private void calcDividend(final Transaction transaction, final SecurityPositionSummary securityPositionSummary,
      final boolean excludeDivTaxcost, final double transactionCost, final double taxCost, final Double exchangeRate) {
    securityPositionSummary.transactionGainLoss = transaction.getSeucritiesNetPrice()
        - ((excludeDivTaxcost) ? 0.0 : taxCost) - transactionCost;

    securityPositionSummary.transactionGainLossPercentage = securityPositionSummary.units > 0
        && securityPositionSummary.adjustedCostBase != 0.0
            ? securityPositionSummary.transactionGainLoss / securityPositionSummary.adjustedCostBase * 100.0
            : null;
    if (excludeDivTaxcost) {
      securityPositionSummary.excludedDivTax += taxCost;
    }
    securityPositionSummary.gainLossSecurity += securityPositionSummary.transactionGainLoss;
    securityPositionSummary.incomeGainLossSecurity += securityPositionSummary.transactionGainLoss;
    securityPositionSummary.positionFlow -= securityPositionSummary.transactionGainLoss;
    if (exchangeRate != null && !transaction.getSecurity().getCurrency().equals(securityPositionSummary.mainCurrency)) {
      securityPositionSummary.transactionFlowSC = -securityPositionSummary.transactionGainLoss;
      securityPositionSummary.transactionGainLossMC = securityPositionSummary.transactionGainLoss * exchangeRate;
      // Income leaves the position just as sale proceeds do, so it belongs in the currency attribution: cash received
      // at the rate of the payment date stops drifting from that moment on. This used to be commented out, which is
      // what the old GAIN_LOSS_CURRENCY_TOOLTIP described when it said dividends and interest were excluded.
      securityPositionSummary.netInvestedSC -= securityPositionSummary.transactionGainLoss;
      securityPositionSummary.netInvestedMC -= securityPositionSummary.transactionGainLoss * exchangeRate;
    }
  }

}
