package grafioschtrader.instrument;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.common.DateHelper;
import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.types.TransactionType;

@Service
public class SecurityCalcService {

  @Autowired
  private NegativeIdNumberCreater negativeIdNumberCreater;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  private final SecurityGeneralCalc securityGeneralCalc;
  private final SecurityMarginCalc securityMarginCalc;

  public SecurityCalcService() {
    securityGeneralCalc = new SecurityGeneralCalc();
    securityMarginCalc = new SecurityMarginCalc();
  }

  /**
   * Calculate all transactions of a security. An open position is not closed.
   *
   * @param excludeDivTaxcost
   * @param securityTransactionSummary
   * @param securitySplitMap
   * @param transactions
   * @param untilDate
   * @param dateCurrencyMap
   */
  public void calcTransactions(Security security, final boolean excludeDivTaxcost,
      final SecurityTransactionSummary securityTransactionSummary,
      final Map<Integer, List<Securitysplit>> securitySplitMap, final List<Transaction> transactions,
      final Date untilDate, DateTransactionCurrencypairMap dateCurrencyMap) {

    boolean isMarginInstrument = securityTransactionSummary.securityPositionSummary.getSecurity().isMarginInstrument();

    if (dateCurrencyMap == null) {
      dateCurrencyMap = new DateTransactionCurrencypairMap(untilDate,
          tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate)));
    }

    for (final Transaction transaction : transactions) {
      if (transaction.getTransactionTime().getTime() > untilDate.getTime()) {
        return;
      }
      if (!isMarginInstrument) {
        securityGeneralCalc.createAccruedInterestPostion(security, TransactionType.REDUCE, transaction,
            securityTransactionSummary, excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater);
      }
      getSecurityCalc(security).calcTransactionAndAddToPosition(transaction,
          securityTransactionSummary, excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater);
      if (!isMarginInstrument) {
        securityGeneralCalc.createAccruedInterestPostion(security, TransactionType.ACCUMULATE, transaction,
            securityTransactionSummary, excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater);
      }
    }
    securityTransactionSummary.securityPositionSummary.removeClosedMarginPosition();
    
  }

  public void calcSingleSecurityTransaction(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    getSecurityCalc(transaction.getSecurity()).calcSingleSecurityTransaction(transaction, summarySecurityMap,
        securitysplitMap, excludeDivTaxcost, dateCurrencyMap, negativeIdNumberCreater,
        globalparametersJpaRepository.getCurrencyPrecision());
  }

  /**
   * One or more open position of security are closed. Margin product may have
   * more than one open position.
   *
   * @param securityPositionSummary
   * @param lastPrice
   * @param securitysplitMap
   * @param dateCurrencyMap
   * @param securityTransactionSummary
   */
  public void createHypotheticalSellTransaction(final SecurityPositionSummary securityPositionSummary,
      final double lastPrice, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, SecurityTransactionSummary securityTransactionSummary) {
    getSecurityCalc(securityPositionSummary.getSecurity()).createHypotheticalSellTransaction(securityPositionSummary,
        lastPrice, securitysplitMap, dateCurrencyMap, securityTransactionSummary, negativeIdNumberCreater);
  }

  private SecurityBaseCalc getSecurityCalc(Security security) {
    return security.isMarginInstrument() ? securityMarginCalc : securityGeneralCalc;

  }

}
