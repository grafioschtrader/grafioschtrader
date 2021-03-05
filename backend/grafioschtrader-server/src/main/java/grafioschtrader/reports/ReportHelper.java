package grafioschtrader.reports;

import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.User;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.repository.HistoryquoteJpaRepository;

public abstract class ReportHelper {

  public static void loadUntilDateHistoryquotes(final HistoryquoteJpaRepository historyquoteJpaRepository,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    // System.out.println("x------------------- Required currency - Date:" +
    // dateCurrencyMap.getUntilDate());
    if (!dateCurrencyMap.isUntilDateEqualNowOrAfter() && !dateCurrencyMap.isUntilDateDataLoaded()) {
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      loadUntilDateHistoryquotesWithoutCheck(user.getIdTenant(), historyquoteJpaRepository, dateCurrencyMap);
    }
  }

  public static void loadUntilDateHistoryquotes(final Integer idTenant,
      final HistoryquoteJpaRepository historyquoteJpaRepository, DateTransactionCurrencypairMap dateCurrencyMap) {
    // System.out.println("x------------------- Required currency - Date:" +
    // dateCurrencyMap.getUntilDate());
    if (!dateCurrencyMap.isUntilDateEqualNowOrAfter() && !dateCurrencyMap.isUntilDateDataLoaded()) {
      loadUntilDateHistoryquotesWithoutCheck(idTenant, historyquoteJpaRepository, dateCurrencyMap);
    }
  }

  private static void loadUntilDateHistoryquotesWithoutCheck(final Integer idTenant,
      final HistoryquoteJpaRepository historyquoteJpaRepository, DateTransactionCurrencypairMap dateCurrencyMap) {
    List<Object[]> currencyList = historyquoteJpaRepository.getUsedCurrencyHistoryquotesByIdTenantAndDate(idTenant,
        dateCurrencyMap.getUntilDate());
    dateCurrencyMap.putToDateFromCurrencyMap(currencyList);
    dateCurrencyMap.untilDateDataIsLoaded();
  }
}
