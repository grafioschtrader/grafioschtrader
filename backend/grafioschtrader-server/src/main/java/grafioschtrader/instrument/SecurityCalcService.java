package grafioschtrader.instrument;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.DisposalEstimate;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TransactionType;

/**
 * It is a service that enables the calculation of returns for a security based on transactions. The calculation
 * distinguishes between margin instruments and other securities. A sale of another security takes its cost by the
 * cost basis method of its security account, which is average cost, FIFO or LIFO, and a specific position is closed for
 * margin instruments. The calculation here is based on existing transactions,
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
   * @param costBasisMethods           The cost basis methods of this report run. Mandatory, so that no caller stays on
   *                                   average cost without noticing.
   */
  public void calcTransactions(Security security, final boolean excludeDivTaxcost,
      final SecurityTransactionSummary securityTransactionSummary,
      final Map<Integer, List<Securitysplit>> securitySplitMap, final List<Transaction> transactions,
      final LocalDate untilDate, DateTransactionCurrencypairMap dateCurrencyMap, CostBasisMethods costBasisMethods) {

    boolean isMarginInstrument = securityTransactionSummary.securityPositionSummary.getSecurity().isMarginInstrument();

    if (dateCurrencyMap == null) {
      dateCurrencyMap = new DateTransactionCurrencypairMap(untilDate,
          tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(untilDate));
    }
    for (final Transaction transaction : sortForCalculation(transactions)) {
      if (transaction.getTransactionTime().toLocalDate().isAfter(untilDate)) {
        return;
      }
      if (!isMarginInstrument) {
        securityGeneralCalc.createAccruedInterestPostion(security, TransactionType.REDUCE, transaction,
            securityTransactionSummary, excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater,
            costBasisMethods);
      }
      getSecurityCalc(security).calcTransactionAndAddToPosition(transaction, securityTransactionSummary,
          excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater, costBasisMethods);
      if (!isMarginInstrument) {
        securityGeneralCalc.createAccruedInterestPostion(security, TransactionType.ACCUMULATE, transaction,
            securityTransactionSummary, excludeDivTaxcost, securitySplitMap, dateCurrencyMap, negativeIdNumberCreater,
            costBasisMethods);
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
   * @param costBasisMethods   The cost basis methods of this report run, resolved once by the report.
   */
  public void calcSingleSecurityTransaction(final Transaction transaction,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap, final CostBasisMethods costBasisMethods) {
    getSecurityCalc(transaction.getSecurity()).calcSingleSecurityTransaction(transaction, summarySecurityMap,
        securitysplitMap, excludeDivTaxcost, dateCurrencyMap, negativeIdNumberCreater,
        globalparametersService.getCurrencyPrecision(), costBasisMethods);
  }

  /**
   * Returns the transactions in the order every calculation walks them, see {@link Transaction#CALC_ORDER}. Queries
   * order by time alone, which leaves transactions of the same minute in an undefined order.
   *
   * @param transactions transactions in any order, left untouched
   * @return a sorted copy
   */
  public static List<Transaction> sortForCalculation(final List<Transaction> transactions) {
    final List<Transaction> sorted = new ArrayList<>(transactions);
    sorted.sort(Transaction.CALC_ORDER);
    return sorted;
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

  /**
   * Creates the hypothetical sale of a non-margin position carrying the estimated disposal costs, so that the
   * transaction list shows the sale with its commission and tax. Margin instruments ignore the estimate, because their
   * disposal costs are not estimated.
   *
   * @param disposalEstimate the estimated costs of the sale in security currency, null for a sale without costs
   * @see #createHypotheticalSellTransaction(SecurityPositionSummary, double, Map, DateTransactionCurrencypairMap,
   *      SecurityTransactionSummary)
   */
  public void createHypotheticalSellTransaction(final SecurityPositionSummary securityPositionSummary,
      final double lastPrice, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, SecurityTransactionSummary securityTransactionSummary,
      DisposalEstimate disposalEstimate) {
    if (disposalEstimate == null || securityPositionSummary.getSecurity().isMarginInstrument()) {
      createHypotheticalSellTransaction(securityPositionSummary, lastPrice, securitysplitMap, dateCurrencyMap,
          securityTransactionSummary);
    } else {
      securityGeneralCalc.createHypotheticalSellTransaction(securityPositionSummary, lastPrice, securitysplitMap,
          dateCurrencyMap, securityTransactionSummary, negativeIdNumberCreater, disposalEstimate);
    }
  }

  private SecurityBaseCalc getSecurityCalc(Security security) {
    return security.isMarginInstrument() ? securityMarginCalc : securityGeneralCalc;
  }

}
