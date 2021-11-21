package grafioschtrader.repository;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import grafioschtrader.dto.DeleteHistoryquotesSuccess;
import grafioschtrader.dto.HistoryquotesWithMissings;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.User;
import grafioschtrader.ta.TaIndicators;
import grafioschtrader.ta.TaTraceIndicatorData;
import grafioschtrader.ta.indicator.model.ShortMediumLongInputPeriod;

public interface HistoryquoteJpaRepositoryCustom extends BaseRepositoryCustom<Historyquote> {

  ISecuritycurrencyIdDateClose getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(Integer idSecuritycurrency,
      String dateString, boolean asTraded) throws ParseException;

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
