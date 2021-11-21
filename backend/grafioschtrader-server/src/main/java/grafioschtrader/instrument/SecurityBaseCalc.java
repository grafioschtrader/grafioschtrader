package grafioschtrader.instrument;

import java.util.Date;
import java.util.List;
import java.util.Map;

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
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

public abstract class SecurityBaseCalc {

  public abstract void calcSingleSecurityTransaction(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap, NegativeIdNumberCreater negativeIdNumberCreater,
      Map<String, Integer> currencyPrecisionMap);

  public abstract void createHypotheticalSellTransaction(final SecurityPositionSummary securityPositionSummary,
      final double lastPrice, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, final SecurityTransactionSummary securityTransactionSummary,
      NegativeIdNumberCreater negativeIdNumberCreater);

  public abstract void calcTransactionAndAddToPosition(final Transaction transaction,
      final SecurityTransactionSummary securityTransactionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitySplitMap, final DateTransactionCurrencypairMap dateCurrencyMap,
      NegativeIdNumberCreater negativeIdNumberCreater);

  protected SecurityPositionSummary getSecurityPositionSummary(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, Map<String, Integer> currencyPrecisionMap) {
    return summarySecurityMap.computeIfAbsent(transaction.getSecurity(),
        key -> new SecurityPositionSummary((dateCurrencyMap != null) ? dateCurrencyMap.getMainCurrency() : null, key,
            currencyPrecisionMap, transaction.getIdSecurityaccount()));
  }

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

    final CalcTransactionPos ctp = new CalcTransactionPos(
        DataHelper.getCurrencyExchangeRateToMainCurreny(transaction, dateCurrencyMap),
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
   * Contain the calculation for a single transaction
   *
   * @author Hugo Graf
   *
   */
  protected static class CalcTransactionPos {
    public final Double exchangeRate;
    public double securitiesNetPrice = 0.0;
    public double transactionTaxCost = 0.0;
    public double taxCost = 0.0;
    public double transactionCost = 0.0;
    public double unitsSplited;
    public double splitFactorFromBaseTransaction;

    public CalcTransactionPos(Double exchangeRate, double splitFactorFromBaseTransaction) {
      this.exchangeRate = exchangeRate;
      this.splitFactorFromBaseTransaction = splitFactorFromBaseTransaction;
    }

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
