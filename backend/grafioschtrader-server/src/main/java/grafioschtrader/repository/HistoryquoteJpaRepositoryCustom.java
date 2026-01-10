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
   * @return an object containing the security or currency ID, applicable date, and (optionally adjusted) close price,
   *         or null if no historical quote is found
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
   * Fills missing historical quotes for non-trading days (weekends and holidays) between two existing quotes.
   * <p>
   * Currency pairs require continuous daily price data for accurate transaction calculations on any calendar day.
   * This method creates placeholder quotes for dates between two existing quotes, copying price data from the day
   * before the gap. Each created quote is marked with {@code HistoryquoteCreateType.FILLED_NON_TRADE_DAY}.
   * </p>
   * <p>
   * <b>Concurrency Safety:</b> This method checks for existing dates before inserting to prevent duplicate key
   * violations when concurrent processes (scheduled EOD updates, user actions) attempt to fill the same gaps.
   * </p>
   *
   * @param dayBeforHoleHistoryquote The historical quote immediately before the gap. Its closing price and other
   *        values are copied to fill the missing days. Must not be null and must have a valid idSecuritycurrency.
   * @param dayAfterHoleHistoryquote The historical quote immediately after the gap. Defines the exclusive upper bound
   *        for the date range to fill. Must not be null.
   */
  void fillMissingPeriodWithHistoryquotes(Historyquote dayBeforHoleHistoryquote, Historyquote dayAfterHoleHistoryquote);

  Auditable getParentSecurityCurrency(final User user, final Historyquote historyquote);

  Auditable getParentSecurityCurrency(final User user, Integer idSecuritycurrency);

  UserAuditable getUserAndCheckSecurityAccess(Integer idSecuritycurrency);

  List<IDateAndClose> getHistoryquoteDateClose(Integer idSecuritycurrency);

  /**
   * Calculates and returns technical analysis (TA) indicator data for a given security or currency pair, based on
   * specified short, medium, and/or long calculation periods.
   * <p>
   * The method fetches historical closing prices for the instrument, excluding any quotes that were specifically
   * generated to fill non-trading days (i.e., where createType is 1, corresponding to
   * HistoryquoteCreateType.FILLED_NON_TRADE_DAY).
   * </p>
   * <p>
   * It then computes the selected TA indicator (e.g., Simple Moving Average - SMA, Exponential Moving Average - EMA)
   * for each of the period lengths (short, medium, long) that are provided (not null) in the shortMediumLongInputPeriod
   * object and for which sufficient historical data exists.
   * </p>
   * <p>
   * Each successfully calculated indicator series is returned as a TaTraceIndicatorData object within the resulting
   * list.
   * </p>
   *
   * @param idSecuritycurrency         The unique identifier for the security or currency pair.
   * @param taIndicator                The technical indicator to calculate (e.g., SMA, EMA).
   * @param shortMediumLongInputPeriod An object specifying the short, medium, and long periods for the TA calculation.
   *                                   A calculation is performed for a period only if it's not null and enough
   *                                   historical data is available.
   * @return A list of TaTraceIndicatorData objects, each representing a trace for a calculated indicator over one of
   *         the specified periods. The list can contain up to three entries, or fewer if some periods are invalid or
   *         lack sufficient data. An empty list is returned if no calculations can be performed.
   */
  List<TaTraceIndicatorData> getTaWithShortMediumLongInputPeriod(Integer idSecuritycurrency, TaIndicators taIndicator,
      ShortMediumLongInputPeriod shortMediumLongInputPeriod);

  <S extends Securitycurrency<S>> HistoryquotesWithMissings<S> getHistoryqoutesByIdSecuritycurrencyWithMissing(
      int idSecuritycurrency, boolean isCurrencypair) throws InterruptedException, ExecutionException;

  void afterDelete(Optional<Historyquote> deletedHistoryquoteOpt);

  DeleteHistoryquotesSuccess deleteHistoryquotesByCreateTypes(Integer idSecuritycurreny,
      List<Byte> historyquoteCreateTypesAsBytes);

}
