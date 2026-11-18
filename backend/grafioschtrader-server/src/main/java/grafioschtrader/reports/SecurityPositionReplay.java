package grafioschtrader.reports;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.SecuritysplitJpaRepository;

/**
 * Walks the security transactions of a set of security accounts through the position calculation. The same security
 * in different security accounts is merged into one {@link SecurityPositionSummary}, keyed by the security.
 *
 * <p>
 * Shared by the security account reports, which value the resulting positions afterwards, and by the period
 * performance per security, which only reads the flow each transaction causes. Each account is walked in
 * {@link SecurityCalcService#sortForCalculation(List)} order; transactions without a security, for example fees, are
 * skipped. The walk runs on the calling thread, because the summaries and the lazily loaded transactions are not safe
 * on another one.
 * </p>
 */
@Component
public class SecurityPositionReplay {

  @Autowired
  private SecurityCalcService securityCalcService;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  /**
   * Processes the selected transactions of the given security accounts.
   *
   * @param securityaccounts   the accounts whose transactions are walked
   * @param summarySecurityMap receives one summary per security; may already hold summaries to continue
   * @param excludeDivTaxcost  if true, tax withholdings of income are not deducted
   * @param dateCurrencyMap    currency context supplying the rate of each transaction
   * @param costBasisMethods   the cost basis methods of the report
   * @param include            selects the transactions to process. Because every account is walked in ascending
   *                           time, a predicate on the transaction time behaves like stopping at the first excluded
   *                           transaction.
   * @param splitObserver      called after each processed transaction with the split map of its account, may be null
   * @param positionObserver   called after each processed transaction with the summary of its security, may be null
   */
  public void replay(final List<Securityaccount> securityaccounts,
      final Map<Security, SecurityPositionSummary> summarySecurityMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap, final CostBasisMethods costBasisMethods,
      final Predicate<Transaction> include,
      final BiConsumer<Transaction, Map<Integer, List<Securitysplit>>> splitObserver,
      final BiConsumer<Transaction, SecurityPositionSummary> positionObserver) {
    for (Securityaccount securityaccount : securityaccounts) {
      final Map<Integer, List<Securitysplit>> securitysplitMap = securitysplitJpaRepository
          .getSecuritysplitMapByIdSecuritycashaccount(securityaccount.getIdSecuritycashAccount());
      for (final Transaction transaction : SecurityCalcService
          .sortForCalculation(securityaccount.getSecurityTransactionList())) {
        if (transaction.getSecurity() != null && include.test(transaction)) {
          securityCalcService.calcSingleSecurityTransaction(transaction, summarySecurityMap, securitysplitMap,
              excludeDivTaxcost, dateCurrencyMap, costBasisMethods);
          if (splitObserver != null) {
            splitObserver.accept(transaction, securitysplitMap);
          }
          if (positionObserver != null) {
            positionObserver.accept(transaction, summarySecurityMap.get(transaction.getSecurity()));
          }
        }
      }
    }
  }
}
