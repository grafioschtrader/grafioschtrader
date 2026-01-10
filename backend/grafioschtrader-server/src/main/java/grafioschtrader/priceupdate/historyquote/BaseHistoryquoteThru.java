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
   * @return
   */
  private List<S> particialFillHistoryquote(List<Integer> idsStockexchange) {

    final Calendar currentCalendar = corretToCalendarForDayAfterUpdate(
        idsStockexchange == null || idsStockexchange.size() == 0);
    final List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList = historyqouteEntityBaseAccess
        .getMaxHistoryquoteResult(globalparametersService.getMaxHistoryRetry(), this, idsStockexchange);

    return fillHistoryquoteForSecuritiesCurrencies(historySecurityCurrencyList, currentCalendar);
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
    final List<S> catchUp = new ArrayList<>();
    ThreadHelper.executeForkJoinPool(
        () -> historySecurityCurrencyList.parallelStream()
            .forEach(queryObject -> catchUpHistoryquote(queryObject, currentCalendar, catchUp)),
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

  private void catchUpHistoryquote(final SecurityCurrencyMaxHistoryquoteData<S> queryObject,
      final Calendar untilCalendar, final List<S> catchUp) {
    final S securitycurrency = queryObject.getSecurityCurrency();
    final Calendar lastQuoteCalendar = DateHelper.getCalendar(queryObject.getDate());
    final int diffInDays = (int) ((untilCalendar.getTimeInMillis() - lastQuoteCalendar.getTimeInMillis())
        / (1000 * 60 * 60 * 24));

    if (diffInDays > 1) {
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
