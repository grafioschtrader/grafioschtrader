package grafioschtrader.repository;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import grafiosch.entities.Auditable;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.dto.DeleteHistoryquotesSuccess;
import grafioschtrader.dto.HistoryquotesWithMissings;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.ta.TaIndicators;
import grafioschtrader.ta.TaTraceIndicatorData;
import grafioschtrader.ta.indicator.model.ShortMediumLongInputPeriod;

public interface HistoryquoteJpaRepositoryCustom extends BaseRepositoryCustom<Historyquote> {

  /**
   * Retrieves the closing price (either raw or adjusted) for a specific security on or before a given date string.
   *
   * This is a convenience method that parses the date from a string and delegates to the main method that accepts a
   * Date object.
   *
   * @param idSecuritycurrency the ID of the security or currency pair
   * @param dateString         the target date as a string (formatted according to SHORT_STANDARD_DATE_FORMAT)
   * @param asTraded           if true, returns the close price adjusted for splits (as-traded), otherwise returns the
   *                           raw close
   * @return an object containing the security or currency ID, applicable date, and (optionally adjusted) close price, or
   *         null if no historical quote is found
   * @throws ParseException if the dateString cannot be parsed
   */
  ISecuritycurrencyIdDateClose getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(Integer idSecuritycurrency,
      String dateString, boolean asTraded) throws ParseException;

  /**
   * Retrieves the closing price (either raw or adjusted) for a specific security on or before a given date.
   *
   * If a matching historical quote is found, and the `asTraded` flag is true, the method applies the appropriate split
   * factor to the close price.
   *
   * @param idSecuritycurrency the ID of the security or currency
   * @param date               the target date
   * @param asTraded           if true, returns the close price adjusted for splits (as-traded), otherwise returns the
   *                           raw close
   * @return an object containing the security currency ID, applicable date, and (optionally adjusted) close price, or
   *         null if no historical quote is found
   */
  ISecuritycurrencyIdDateClose getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(final Integer idSecuritycurrency,
      final Date date, final boolean asTraded);

  /**
   * Create history quotes for weekend and public holidays
   *
   * @param dayBeforHoleHistoryquote
   * @param dayAfterHoleHistoryquote
   */
  void fillMissingPeriodWithHistoryquotes(Historyquote dayBeforHoleHistoryquote, Historyquote dayAfterHoleHistoryquote);

  Auditable getParentSecurityCurrency(final User user, final Historyquote historyquote);

  Auditable getParentSecurityCurrency(final User user, Integer idSecuritycurrency);

  UserAuditable getUserAndCheckSecurityAccess(Integer idSecuritycurrency);

  List<IDateAndClose> getHistoryquoteDateClose(Integer idSecuritycurrency);

  List<TaTraceIndicatorData> getTaWithShortMediumLongInputPeriod(Integer idSecuritycurrency, TaIndicators taIndicator,
      ShortMediumLongInputPeriod shortMediumLongInputPeriod);

  <S extends Securitycurrency<S>> HistoryquotesWithMissings<S> getHistoryqoutesByIdSecuritycurrencyWithMissing(
      int idSecuritycurrency, boolean isCurrencypair) throws InterruptedException, ExecutionException;

  void afterDelete(Optional<Historyquote> deletedHistoryquoteOpt);

  DeleteHistoryquotesSuccess deleteHistoryquotesByCreateTypes(Integer idSecuritycurreny,
      List<Byte> historyquoteCreateTypesAsBytes);

}
