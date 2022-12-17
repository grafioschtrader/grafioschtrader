package grafioschtrader.instrument;

import java.util.List;
import java.util.Map;

import grafioschtrader.common.DataHelper;
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
 * Calculate a the security risk and gain/loss of standard investing products
 * like bond, stock, etc.
 *
 * @author Hugo Graf
 *
 */
public class SecurityGeneralCalc extends SecurityBaseCalc {

  @Override
  public void calcSingleSecurityTransaction(final Transaction transaction,
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

  @Override
  public void createHypotheticalSellTransaction(SecurityPositionSummary securityPositionSummary, double lastPrice,
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

  @Override
  public void calcTransactionAndAddToPosition(final Transaction transaction,
      final SecurityTransactionSummary securityTransactionSummary, final boolean excludeDivTaxcost,
      final Map<Integer, List<Securitysplit>> securitySplitMap, final DateTransactionCurrencypairMap dateCurrencyMap,
      NegativeIdNumberCreater negativeIdNumberCreater) {
    calcTransactionPosition(transaction, securityTransactionSummary.securityPositionSummary, excludeDivTaxcost,
        securitySplitMap, false, dateCurrencyMap, negativeIdNumberCreater);
    securityTransactionSummary.createAndAddPositionGainLoss(transaction);
  }

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
   * The central calculation of a security, including foreign currency.
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
        ? DataHelper.round(securityPositionSummary.transactionGainLoss)
        : null;
  }

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
