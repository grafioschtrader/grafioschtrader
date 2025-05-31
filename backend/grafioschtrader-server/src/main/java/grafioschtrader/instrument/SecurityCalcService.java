package grafioschtrader.instrument;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.common.DateHelper;
import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TransactionType;

/**
 * It is a service that enables the calculation of returns for a security based on transactions. The calculation
 * distinguishes between margin instruments and other securities. The calculation is based on the Average Cost Method
 * and a specific position is closed for margin instruments. The calculation here is based on existing transactions,
 * users of this service may have to perform further calculations for transactions that have not yet been closed.
 */
@Service
public class SecurityCalcService {

  @Autowired
  private NegativeIdNumberCreater negativeIdNumberCreater;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  private final SecurityGeneralCalc securityGeneralCalc;
  private final SecurityMarginCalc securityMarginCalc;

  public SecurityCalcService() {
    securityGeneralCalc = new SecurityGeneralCalc();
    securityMarginCalc = new SecurityMarginCalc();
  }

  /**
   * Calculate all transactions of a security. An open position is not closed.
   *
   * @param security                   The security concerned
   * @param excludeDivTaxcost          If true, tax withholdings are not taken into account.
   * @param securityTransactionSummary The hypothetical transaction(s) are added here.
   * @param securitySplitMap           Contains the splits. ID of the security and the list of splits.
   * @param transactions               Transaction for this security
   * @param untilDate                  The most recent date up to which the transactions are evaluated.
   * @param dateCurrencyMap            Contains the necessary information for the calculation to work with different
   *                                   currencies.
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
      getSecurityCalc(security).calcTransactionAndAddToPosition(transaction, securityTransactionSummary,
          excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater);
      if (!isMarginInstrument) {
        securityGeneralCalc.createAccruedInterestPostion(security, TransactionType.ACCUMULATE, transaction,
            securityTransactionSummary, excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater);
      }
    }
    securityTransactionSummary.securityPositionSummary.removeClosedMarginPosition();

  }

  /**
   * Processing of a securities transaction. The return is calculated in the process. It does not matter whether the
   * position is closed or open. The transactions should be transferred in ascending order of execution. This means
   * sorting by cash account or custody account and then by transaction time.
   *
   * @param transaction        The transaction
   * @param summarySecurityMap Contains the calculations for each individual security.
   * @param securitysplitMap   Contains the splits. ID of the security and the list of splits.
   * @param excludeDivTaxcost  If true, tax withholdings are not taken into account.
   * @param dateCurrencyMap    Contains the necessary information for the calculation to work with different currencies.
   */
  public void calcSingleSecurityTransaction(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    getSecurityCalc(transaction.getSecurity()).calcSingleSecurityTransaction(transaction, summarySecurityMap,
        securitysplitMap, excludeDivTaxcost, dateCurrencyMap, negativeIdNumberCreater,
        globalparametersService.getCurrencyPrecision());
  }

  /**
   * It may be useful for the user to be able to see the hypothetical closing of the position. A hypothetical
   * translation is therefore created for this purpose. In the case of a margin instrument, these can be several closing
   * positions, otherwise only one.
   *
   * @param securityPositionSummary    The corresponding summary of the security is updated with the hypothetical
   *                                   transaction.
   * @param lastPrice                  The price on which the hypothetical transaction is based.
   * @param securitysplitMap           Contains the splits. ID of the security and the list of splits.
   * @param dateCurrencyMap            Contains the necessary information for the calculation to work with different
   *                                   currencies.
   * @param securityTransactionSummary The hypothetical transaction(s) are added here.
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
