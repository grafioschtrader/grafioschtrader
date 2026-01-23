package grafioschtrader.priceupdate.historyquote;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.common.DateHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.ThreadHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.priceupdate.BaseQuoteThru;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.repository.SecurityServiceAsyncExectuion;
import grafioschtrader.repository.SecuritycurrencyService;
import grafioschtrader.service.GlobalparametersService;

/*-
 * This is the base class for loading or calculating historical price data.
 *
 * @param <S>
 */
public abstract class BaseHistoryquoteThru<S extends Securitycurrency<S>> extends BaseQuoteThru
    implements IHistoryquoteLoad<S> {
  protected final String LINK_DOWNLOAD_LAZY = "lazy";

  protected final GlobalparametersService globalparametersService;
  private final IHistoryqouteEntityBaseAccess<S> historyqouteEntityBaseAccess;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  protected abstract List<S> fillEmptyHistoryquote();

  public BaseHistoryquoteThru(GlobalparametersService globalparametersService,
      IHistoryqouteEntityBaseAccess<S> historyqouteEntityBaseAccess) {
    this.globalparametersService = globalparametersService;
    this.historyqouteEntityBaseAccess = historyqouteEntityBaseAccess;
  }

  @Override
  public List<S> catchAllUpSecuritycurrencyHistoryquote(List<Integer> idsStockexchange) {
    final List<S> catchUp = fillEmptyHistoryquote();
    catchUp.addAll(particialFillHistoryquote(idsStockexchange));
    return catchUp;
  }

  @Override
  public <U extends SecuritycurrencyPositionSummary<S>> void reloadAsyncFullHistoryquote(
      final SecurityServiceAsyncExectuion<S, U> securityServiceAsyncExectuion,
      final SecuritycurrencyService<S, U> securitycurrencyService, final S securitycurrency) {

    Hibernate.initialize(securitycurrency.getHistoryquoteList());
    securityServiceAsyncExectuion.asyncLoadHistoryIntraData(securitycurrencyService, securitycurrency, true,
        globalparametersService.getMaxIntraRetry(), globalparametersService.getSecurityCurrencyIntradayUpdateTimeout());
  }

  /**
   * For all securities and currencies it fills their history quote from the youngest entry date until now.
   *
   * @param idsStockexchange list of stock exchange IDs, or null/empty for global update
   * @return list of updated securities or currency pairs
   */
  private List<S> particialFillHistoryquote(List<Integer> idsStockexchange) {
    final boolean isExchangeSpecificUpdate = idsStockexchange != null && !idsStockexchange.isEmpty();
    final Calendar currentCalendar = corretToCalendarForDayAfterUpdate(!isExchangeSpecificUpdate);
    final List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList = historyqouteEntityBaseAccess
        .getMaxHistoryquoteResult(globalparametersService.getMaxHistoryRetry(), this, idsStockexchange);

    return fillHistoryquoteForSecuritiesCurrencies(historySecurityCurrencyList, currentCalendar, isExchangeSpecificUpdate);
  }

  /**
   * For current Sunday or Monday only determine EOD until previous Friday.
   *
   * @param adjustForDayAfterUpd If the dates are determined according to the closing times of the trading exchange, the
   *                             to-date is determined differently.
   * @return
   */
  private Calendar corretToCalendarForDayAfterUpdate(boolean adjustForDayAfterUpd) {
    final Calendar currentCalendar = DateHelper.getCalendar(new Date());

    if (adjustForDayAfterUpd && (currentCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        || currentCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)) {
      currentCalendar.add(Calendar.DATE, currentCalendar.get(Calendar.DAY_OF_WEEK) * -1);
    }
    return currentCalendar;
  }

  @Override
  public List<S> fillHistoryquoteForSecuritiesCurrencies(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, final Calendar currentCalendar) {
    return fillHistoryquoteForSecuritiesCurrencies(historySecurityCurrencyList, currentCalendar, false);
  }

  /**
   * Updates historical quotes for a list of currency pairs and securities.
   *
   * @param historySecurityCurrencyList list of securities/currencies with their maximum historical quote dates
   * @param currentCalendar current calendar for determining the update range
   * @param isExchangeSpecificUpdate true if this is an exchange-specific update (allows single-day updates),
   *                                  false for global daily update (requires more than 1 day difference)
   * @return list of updated securities or currency pairs
   */
  private List<S> fillHistoryquoteForSecuritiesCurrencies(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, final Calendar currentCalendar,
      boolean isExchangeSpecificUpdate) {
    final List<S> catchUp = new ArrayList<>();
    ThreadHelper.executeForkJoinPool(
        () -> historySecurityCurrencyList.parallelStream()
            .forEach(queryObject -> catchUpHistoryquote(queryObject, currentCalendar, catchUp, isExchangeSpecificUpdate)),
        GlobalConstants.FORK_JOIN_POOL_CORE_MULTIPLIER);
    return catchUp;
  }

  protected List<S> catchUpEmptyHistoryquote(List<S> historySecurityCurrencyList) {
    final List<S> catchUp = new ArrayList<>();
    ThreadHelper.executeForkJoinPool(() -> historySecurityCurrencyList.parallelStream().forEach(securitycurrency -> {
      log.info("Create history quote for securitycurrency={}", securitycurrency);
      final S execSecuritycurrency = historyqouteEntityBaseAccess.catchUpSecurityCurrencypairHisotry(securitycurrency,
          null, null);
      if (execSecuritycurrency.getRetryHistoryLoad() == 0) {
        catchUp.add(execSecuritycurrency);
      }
    }), GlobalConstants.FORK_JOIN_POOL_CORE_MULTIPLIER);
    return catchUp;
  }

  protected void addHistoryquotesToSecurity(S securitycurrency, List<Historyquote> historyquotes, Date fromDate,
      Date toDate) {
    if (securitycurrency.getHistoryquoteList() == null || securitycurrency.getHistoryquoteList().isEmpty()) {
      securitycurrency.setHistoryquoteList(historyquotes);
    } else {
      for (final Historyquote historyquote : historyquotes) {
        securitycurrency.getHistoryquoteList().add(historyquote);
      }
    }
    if (fromDate == null && toDate == null) {
      securitycurrency.setFullLoadTimestamp(new Date(System.currentTimeMillis()));
    }
  }

  protected Date getCorrectedFromDate(final S securitycurrency, final Date fromDate) throws ParseException {
    if (fromDate == null) {
      return securitycurrency instanceof Security ? ((Security) securitycurrency).getActiveFromDate()
          : this.globalparametersService.getStartFeedDate();
    } else {
      return fromDate;
    }
  }

  /**
   * Updates historical quotes for a single security or currency pair if there are missing days.
   *
   * @param queryObject the security/currency with its maximum historical quote date
   * @param untilCalendar the target date to update to
   * @param catchUp list to add successfully updated securities to
   * @param isExchangeSpecificUpdate true if this is an exchange-specific update. For exchange-specific updates,
   *                                  securities are updated if diffInDays >= 1 (allows same-day updates after
   *                                  exchange closes). For global daily updates, diffInDays must be > 1.
   */
  private void catchUpHistoryquote(final SecurityCurrencyMaxHistoryquoteData<S> queryObject,
      final Calendar untilCalendar, final List<S> catchUp, boolean isExchangeSpecificUpdate) {
    final S securitycurrency = queryObject.getSecurityCurrency();
    final Calendar lastQuoteCalendar = DateHelper.getCalendar(queryObject.getDate());
    final int diffInDays = (int) ((untilCalendar.getTimeInMillis() - lastQuoteCalendar.getTimeInMillis())
        / (1000 * 60 * 60 * 24));

    // For exchange-specific updates: update if at least 1 day difference (allows today's data after close)
    // For global daily updates: update only if more than 1 day difference
    final int minDaysRequired = isExchangeSpecificUpdate ? 1 : 2;
    if (diffInDays >= minDaysRequired) {
      log.debug("Catchup historyquote, missing Days: diffInDays={} for Security/Currency securitycurrency={}",
          diffInDays, securitycurrency);
      lastQuoteCalendar.add(Calendar.DATE, 1);
      final S execSecuritycurrency = historyqouteEntityBaseAccess.catchUpSecurityCurrencypairHisotry(securitycurrency,
          lastQuoteCalendar.getTime(), untilCalendar.getTime());
      if (execSecuritycurrency.getRetryHistoryLoad() == 0) {
        catchUp.add(execSecuritycurrency);
      }
    }
  }

}
