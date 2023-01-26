package grafioschtrader.priceupdate.historyquote;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.context.MessageSource;

import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.repository.ISecuritycurrencyService;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityServiceAsyncExectuion;
import grafioschtrader.repository.SecuritycurrencyService;

/**
 * Current and historical price data of instruments can arise differently. They
 * can be loaded from an external data source or derived from existing
 * instruments. Therefore this interface exists.
 *
 * @param <S>
 */
public interface IHistoryquoteLoad<S extends Securitycurrency<S>> {

  /**
   * Complete the history for all security or currency pairs until yesterday's
   * date.
   *
   * @return
   */
  List<S> catchAllUpSecuritycurrencyHistoryquote(List<Integer> idsStockexchange);

  /**
   * It gets history quotes of a security or currency pair for certain time period
   * and persist it.</br>
   * This operation may take a some time, because of that, the security or
   * currency pair should not be changed from another thread. The data is
   * persisted.
   *
   * @param jpaRepository    JPA Repository is used for saving the currency pair
   *                         or security
   * @param securitycurrency Security or currency pair
   * @param fromDate         When null then Date from global parameters is taken
   * @param toDate           When null then actual Date is taken
   */
  S createHistoryQuotesAndSave(final ISecuritycurrencyService<S> securitycurrencyService, final S securitycurrency,
      final Date fromDate, final Date toDate);

  /**
   * Update history quotes from the list of currency pairs and securities. There
   * is no check for retry, that means it is ignored.
   *
   * @param historySecurityCurrencyList
   * @param currentCalendar
   * @return
   */
  List<S> fillHistoryquoteForSecuritiesCurrencies(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, final Calendar currentCalendar);

  /**
   * Some cases the historical prices must be reloaded completely. For example
   * when a split is added.
   *
   * @param securitycurrency
   */
  <U extends SecuritycurrencyPositionSummary<S>> void reloadAsyncFullHistoryquote(
      final SecurityServiceAsyncExectuion<S, U> securityServiceAsyncExectuion,
      final SecuritycurrencyService<S, U> securitycurrencyService, final S securitycurrency);

  /**
   * Gets the download link for historical data string.
   *
   * @param securitycurrency
   * @return
   */
  String getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(S securitycurrency);

  HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy,
      SecurityJpaRepository securityJpaRepository, MessageSource messages);
}
