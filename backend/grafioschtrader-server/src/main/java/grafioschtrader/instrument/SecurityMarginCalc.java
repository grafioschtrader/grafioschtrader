package grafioschtrader.instrument;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.common.DataHelper;
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
 * Calculate the security risk and gain/loss of margin product.
 *
 */
public class SecurityMarginCalc extends SecurityBaseCalc {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public void calcSingleSecurityTransaction(Transaction transaction,
      Map<Security, SecurityPositionSummary> summarySecurityMap, Map<Integer, List<Securitysplit>> securitysplitMap,
      boolean excludeDivTaxcost, DateTransactionCurrencypairMap dateCurrencyMap,
      NegativeIdNumberCreater negativeIdNumberCreater, Map<String, Integer> currencyPrecisionMap) {

    SecurityPositionSummary securityPositionSummary = getSecurityPositionSummary(transaction, summarySecurityMap,
        dateCurrencyMap, currencyPrecisionMap);

    calcTransactionPosition(transaction, securityPositionSummary, excludeDivTaxcost, securitysplitMap, true,
        dateCurrencyMap);

  }

  @Override
  public void createHypotheticalSellTransaction(SecurityPositionSummary securityPositionSummary, double lastPrice,
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

  @Override
  public void calcTransactionAndAddToPosition(Transaction transaction,
      SecurityTransactionSummary securityTransactionSummary, boolean excludeDivTaxcost,
      Map<Integer, List<Securitysplit>> securitySplitMap, DateTransactionCurrencypairMap dateCurrencyMap,
      NegativeIdNumberCreater negativeIdNumberCreater) {
    calcTransactionPosition(transaction, securityTransactionSummary.securityPositionSummary, excludeDivTaxcost,
        securitySplitMap, false, dateCurrencyMap);
    securityTransactionSummary.createAndAddPositionGainLoss(transaction);

  }

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
