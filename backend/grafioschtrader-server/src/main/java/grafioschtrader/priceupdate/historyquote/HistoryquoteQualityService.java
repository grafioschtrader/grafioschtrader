package grafioschtrader.priceupdate.historyquote;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import grafioschtrader.dto.HisotryqouteLinearFilledSummary;
import grafioschtrader.dto.HistoryquoteFillGapsBounds;
import grafioschtrader.dto.HistoryquoteFillGapsParam;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.types.HistoryquoteCreateType;

@Service
public class HistoryquoteQualityService {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private MessageSource messages;

  @Autowired
  private DailyLimitService dailyLimitService;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Transactional
  public HisotryqouteLinearFilledSummary fillHistoryquoteGapsLinear(final Integer idSecuritycurrency,
      final HistoryquoteFillGapsParam fillGapsParam) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Double[] firstLastPrice = new Double[2];
    final List<LocalDate> missingDays = new ArrayList<>();
    final List<Historyquote> missingHistoryquoteList = new ArrayList<>();
    HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary = new HisotryqouteLinearFilledSummary();

    Security security = securityJpaRepository.getReferenceById(idSecuritycurrency);
    assertMayFillGaps(user, security);
    // The end date is chosen by the user, so the limit the dialog offers has to be enforced here as well. A date beyond
    // it would create prices for days that neither the trading calendar of the exchange nor the lifetime of the
    // instrument covers.
    final LocalDate maxFillUpTo = maxFillUpTo(security);
    if (fillGapsParam.fillUpToDate.isAfter(maxFillUpTo)) {
      throw new DataViolationException("fill.up.to.date", "gt.fill.up.to.date.max",
          new Object[] { maxFillUpTo.toString() });
    }
    // Filling the gaps of an instrument is accounted as a single edit of that instrument, so it consumes the same
    // daily budget as a manual change to it. Checked before the first row is written or moved.
    dailyLimitService.check(user, Security.class.getSimpleName(), 1);
    if (fillGapsParam.moveWeekendToFriday) {
      moveWeekendDayToBusinessDay(idSecuritycurrency, hisotryqouteLinearFilledSummary);
    }

    List<IDateAndClose> dateAndClose = historyquoteJpaRepository
        .getClosedAndMissingHistoryquoteByIdSecurity(idSecuritycurrency, fillGapsParam.fillUpToDate);
    dateAndClose.forEach(dac -> {
      hisotryqouteLinearFilledSummary.requiredClosing++;
      if (dac.getClose() == null) {
        missingDays.add(dac.getDate());
      } else if (missingDays.isEmpty()) {
        firstLastPrice[0] = dac.getClose();
      } else {
        // Get close price but missing days is not empty
        firstLastPrice[1] = dac.getClose();
        addHistoryquotesLiniear(idSecuritycurrency, firstLastPrice, missingDays, missingHistoryquoteList,
            hisotryqouteLinearFilledSummary);
      }
    });

    this.hisotryqouteLinearFill(user, idSecuritycurrency, firstLastPrice, missingDays, hisotryqouteLinearFilledSummary,
        missingHistoryquoteList);
    dailyLimitService.log(user.getIdUser(), Security.class.getSimpleName(), OperationType.UPDATE, 1);

    return hisotryqouteLinearFilledSummary;
  }

  /**
   * Determines the date boundaries the fill gaps dialog offers for the given instrument.
   * <p>
   * The selectable range ends where the trading calendar of the exchange or the lifetime of the instrument ends, which
   * may well lie in the future — a bond whose active to date is its maturity is the normal case. Prices for such future
   * days are deliberately reachable, but only when the user moves the date forward, because the proposal stops at the
   * current day. When the calendar does not even reach the last completed trading day, {@code calendarOutdated}
   * announces that filling cannot get that far.
   *
   * @param idSecuritycurrency the identifier of the security
   * @return the boundaries, never null
   */
  @Transactional(readOnly = true)
  public HistoryquoteFillGapsBounds getFillGapsBounds(final Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Security security = securityJpaRepository.findById(idSecuritycurrency)
        .orElseThrow(() -> new SecurityException(BaseConstants.STEAL_DATA_SECURITY_BREACH));
    assertMayFillGaps(user, security);

    final LocalDate today = LocalDate.now();
    final LocalDate calendarHorizon = security.getStockexchange().getCalendarKnownUntil();
    final TradingDaysPlus lastTradingDay = tradingDaysPlusJpaRepository
        .findTopByTradingDateLessThanOrderByTradingDateDesc(today);
    // Without a global calendar there is nothing to compare against, the horizon itself is then the best guess.
    final LocalDate lastCompletedTradingDay = lastTradingDay == null ? calendarHorizon
        : lastTradingDay.getTradingDate();

    HistoryquoteFillGapsBounds bounds = new HistoryquoteFillGapsBounds();
    bounds.maxFillUpTo = maxFillUpTo(security);
    // An instrument starting after its own end would otherwise produce an empty picker range.
    bounds.minFillUpTo = min(security.getActiveFromDate(), bounds.maxFillUpTo);
    bounds.calendarHorizon = calendarHorizon;
    // The current day is only a proposal. Reaching further, up to the maturity of a bond for instance, stays a
    // deliberate act of the user.
    bounds.defaultFillUpTo = max(bounds.minFillUpTo, min(bounds.maxFillUpTo, today));
    // Only warn when the calendar is the limiting factor. For an instrument whose active to date has passed the
    // proposed date lies in the past for a legitimate reason, and extending the calendar would change nothing.
    bounds.calendarOutdated = calendarHorizon.isBefore(lastCompletedTradingDay)
        && security.getActiveToDate().isAfter(calendarHorizon);
    if (bounds.calendarOutdated) {
      bounds.missingTradingDays = (int) tradingDaysPlusJpaRepository
          .countByTradingDateBetween(calendarHorizon.plusDays(1), lastCompletedTradingDay);
    }
    return bounds;
  }

  /**
   * Last date a linear filling may reach for this instrument: the trading calendar of its exchange has to know the day,
   * and the instrument has to be active on it. The result may lie in the future, which is why the boundary is offered
   * to the user instead of being cut at the current day.
   *
   * @param security the instrument whose prices would be created
   * @return the latest fillable date
   */
  private static LocalDate maxFillUpTo(final Security security) {
    return min(security.getActiveToDate(), security.getStockexchange().getCalendarKnownUntil());
  }

  private static LocalDate min(final LocalDate left, final LocalDate right) {
    return left.isBefore(right) ? left : right;
  }

  private static LocalDate max(final LocalDate left, final LocalDate right) {
    return left.isAfter(right) ? left : right;
  }

  /**
   * Only the owner of an instrument that is no longer active, or an administrator, may create or move prices in bulk.
   * The same gate protects the boundaries endpoint, so they cannot be probed by a user who is not allowed to fill.
   *
   * @param user     the acting user
   * @param security the instrument whose prices would be touched
   */
  private void assertMayFillGaps(final User user, final Security security) {
    if (!((UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)
        && security.getActiveToDate().isBefore(LocalDate.now()) && security.getIdTenantPrivate() == null)
        || UserAccessHelper.isAdmin(user))) {
      throw new SecurityException(BaseConstants.STEAL_DATA_SECURITY_BREACH);
    }
  }

  private void hisotryqouteLinearFill(final User user, final Integer idSecuritycurrency, final Double[] firstLastPrice,
      final List<LocalDate> missingDays, HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary,
      final List<Historyquote> missingHistoryquoteList) {
    if (!missingDays.isEmpty()) {
      if (firstLastPrice[0] != null || firstLastPrice[1] != null) {
        addHistoryquotesLiniear(idSecuritycurrency, firstLastPrice, missingDays, missingHistoryquoteList,
            hisotryqouteLinearFilledSummary);
      } else {
        // Not a single day with a close price was found
        hisotryqouteLinearFilledSummary.message = messages.getMessage("gt.not.single.valid.close", null,
            user.createAndGetJavaLocale());
        hisotryqouteLinearFilledSummary.warning = true;
      }
    }
    if (!hisotryqouteLinearFilledSummary.warning) {
      hisotryqouteLinearFilledSummary.message = messages.getMessage("gt.success", null, user.createAndGetJavaLocale());
    }
    historyquoteJpaRepository.saveAll(missingHistoryquoteList);
    hisotryqouteLinearFilledSummary.createdHistoryquotes = missingHistoryquoteList.size();

  }

  private void moveWeekendDayToBusinessDay(final Integer idSecuritycurrency,
      HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary) {
    List<Historyquote> historyquotes = historyquoteJpaRepository.findByIdFridayAndWeekend(idSecuritycurrency);
    List<Historyquote> historyquotesForDelete = new ArrayList<>();
    List<Historyquote> historyquotesForSave = new ArrayList<>();
    for (int i = 0; i < historyquotes.size() - 1; i++) {
      Historyquote historyquote = historyquotes.get(i);
      LocalDate localDate = historyquote.getDate();
      if (localDate.getDayOfWeek() == DayOfWeek.SUNDAY || localDate.getDayOfWeek() == DayOfWeek.SATURDAY) {
        LocalDate dayBefore = localDate.minusDays(1);
        LocalDate historyqouteDayBefore = historyquotes.get(i + 1).getDate();
        if (dayBefore.isEqual(historyqouteDayBefore)) {
          // Remove it -> exists other history quote
          historyquotesForDelete.add(historyquote);
        } else {
          historyquote.setDate(dayBefore);
          if (localDate.getDayOfWeek() == DayOfWeek.SATURDAY) {
            // Modify Saturday history quote to Friday
            historyquotesForSave.add(historyquote);
          } else {
            i++;
          }
        }
      }
    }
    hisotryqouteLinearFilledSummary.removedWeekendDays = historyquotesForDelete.size();
    historyquoteJpaRepository.deleteAll(historyquotesForDelete);
    hisotryqouteLinearFilledSummary.movedWeekendDays = historyquotesForSave.size();
    historyquoteJpaRepository.saveAll(historyquotesForSave);
  }

  private void addHistoryquotesLiniear(final Integer idSecuritycurrency, final Double[] firstLastPrice,
      final List<LocalDate> missingDays, final List<Historyquote> missingHistoryquoteList,
      HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary) {
    Double slope = null;
    double startPrice = 0;
    if (firstLastPrice[0] != null && firstLastPrice[1] != null) {
      // Price before and after gap is available
      slope = (firstLastPrice[1] - firstLastPrice[0]) / (missingDays.size() + 1);
      startPrice = firstLastPrice[0];
    } else if (firstLastPrice[0] != null && firstLastPrice[1] == null
        || firstLastPrice[0] == null && firstLastPrice[1] != null) {
      // Price only before -> Gap on the beginning of history quotes or Price only
      // after -> Gap on the end of history
      // quotes
      slope = 0.0;
      if (firstLastPrice[0] != null) {
        startPrice = firstLastPrice[0];
        hisotryqouteLinearFilledSummary.createdHistoryquotesEnd = missingDays.size();
      } else {
        startPrice = firstLastPrice[1];
        hisotryqouteLinearFilledSummary.createdHistoryquotesStart = missingDays.size();
      }

    }
    if (slope != null) {
      for (int i = 0; i < missingDays.size(); i++) {
        missingHistoryquoteList
            .add(new Historyquote(idSecuritycurrency, HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY,
                missingDays.get(i), startPrice + (i + 1) * slope));
      }
      hisotryqouteLinearFilledSummary.gapsTotalFilled++;
      missingDays.clear();
      firstLastPrice[0] = firstLastPrice[1];
      firstLastPrice[1] = null;

    }
  }
}
