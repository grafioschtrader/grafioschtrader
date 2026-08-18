package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import grafioschtrader.GlobalConstants;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.dto.CopyTradingDaysFromSourceToTarget;
import grafioschtrader.dto.TradingDaysWithDateBoundaries;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingDaysMinus;
import jakarta.transaction.Transactional;

public class TradingDaysMinusJpaRepositoryImpl implements TradingDaysMinusJpaRepositoryCustom {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private DailyLimitService dailyLimitService;

  @Override
  @Transactional
  public TradingDaysWithDateBoundaries getTradingDaysByIdStockexchangeAndYear(int idStockexchange, int year) {
    LocalDate fromDate = LocalDate.of(year, 1, 1);
    LocalDate toDate = LocalDate.of(year, 12, 31);
    List<TradingDaysMinus> tradingDaysMinusList = tradingDaysMinusJpaRepository
        .findByTradingDaysMinusKey_IdStockexchangeAndTradingDaysMinusKey_TradingDateMinusBetween(idStockexchange,
            fromDate, toDate);

    return new TradingDaysWithDateBoundaries(
        tradingDaysMinusList.stream().map(TradingDaysMinus::getTradingDateMinus).collect(Collectors.toList()),
        tradingDaysMinusList.stream().map(TradingDaysMinus::getCreateType).collect(Collectors.toList()));
  }

  @Override
  public TradingDaysWithDateBoundaries save(int idStockexchange, SaveTradingDays saveTradingDays) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    checkExitsAndRightsForTargetIdStockexhchange(idStockexchange, user);
    List<TradingDaysMinus> createTradingDaysMinusList = new ArrayList<>();
    List<TradingDaysMinus> deleteTradingDaysMinusList = new ArrayList<>();
    Set<Integer> touchedYears = new HashSet<>();
    for (AddRemoveDay addRemoveDay : saveTradingDays.addRemoveDays) {
      touchedYears.add(addRemoveDay.date.getYear());
      if (addRemoveDay.add) {
        createTradingDaysMinusList.add(new TradingDaysMinus(idStockexchange, addRemoveDay.date));
      } else {
        deleteTradingDaysMinusList.add(new TradingDaysMinus(idStockexchange, addRemoveDay.date));
      }
    }
    // The years are taken from the posted dates rather than from saveTradingDays.year: that field only says which year
    // the response is rendered for and nothing forces the dates into it.
    checkAndLogTouchedYears(user, touchedYears.size());

    tradingDaysMinusJpaRepository.saveAll(createTradingDaysMinusList);
    tradingDaysMinusJpaRepository.deleteAllInBatch(deleteTradingDaysMinusList);

    return getTradingDaysByIdStockexchangeAndYear(idStockexchange, saveTradingDays.year);
  }

  @Override
  public TradingDaysWithDateBoundaries copyTradingDaysMinusToOtherStockexchange(
      CopyTradingDaysFromSourceToTarget ctdfstt, boolean excludeCheck) {
    LocalDate dateFrom = ctdfstt.fullCopy ? LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY)
        : LocalDate.of(ctdfstt.returnOrCopyYear, 1, 1);
    LocalDate dateTo = ctdfstt.fullCopy ? LocalDate.parse(GlobalConstants.YOUNGEST_TRADING_CALENDAR_DAY)
        : LocalDate.of(ctdfstt.returnOrCopyYear, 12, 31);
    if (!excludeCheck) {
      checkCopyTradingDaysMinusToOtherStockexchange(ctdfstt);
      // A full copy spans every year GT supports and therefore costs that many units, which is what keeps it from
      // being the cheap way around the per-year budget.
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      checkAndLogTouchedYears(user, dateTo.getYear() - dateFrom.getYear() + 1);
    }
    tradingDaysMinusJpaRepository.copyTradingMinusToOtherStockexchange(ctdfstt.sourceIdStockexchange,
        ctdfstt.targetIdStockexchange, dateFrom, dateTo);
    return getTradingDaysByIdStockexchangeAndYear(ctdfstt.targetIdStockexchange, ctdfstt.returnOrCopyYear);
  }

  /**
   * Charges a calendar change against the daily budget of the trading calendar and books it.
   *
   * <p>
   * The unit is one stock exchange year touched, not one {@code trading_days_minus} row: a calendar is always edited or
   * copied a year at a time, so a budget in rows would make a sparse year and a dense one cost wildly different amounts
   * for the same amount of work. The check runs before the first row is written or deleted, so a request that does not
   * fit changes nothing at all.
   * </p>
   *
   * @param user  the user performing the change
   * @param years how many stock exchange years the change touches; nothing happens when it touches none
   */
  private void checkAndLogTouchedYears(User user, int years) {
    if (years > 0) {
      dailyLimitService.check(user, LimitKeyConfig.ENTITY_NAME_TRADING_DAYS_MINUS_YEAR, years);
      dailyLimitService.log(user.getIdUser(), LimitKeyConfig.ENTITY_NAME_TRADING_DAYS_MINUS_YEAR, OperationType.ADD,
          years);
    }
  }

  private void checkCopyTradingDaysMinusToOtherStockexchange(CopyTradingDaysFromSourceToTarget ctdfstt) {
    if (ctdfstt.sourceIdStockexchange != ctdfstt.targetIdStockexchange) {
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      Optional<Stockexchange> stockexchangeOpt = stockexchangeJpaRepository
          .findByIdStockexchangeAndNoMarketValueFalse(ctdfstt.sourceIdStockexchange);
      if (stockexchangeOpt.isPresent()) {
        checkExitsAndRightsForTargetIdStockexhchange(ctdfstt.targetIdStockexchange, user);
      } else {
        throw new NoSuchElementException(messageSource.getMessage("entity.not.found",
            new Object[] { ctdfstt.sourceIdStockexchange }, user.createAndGetJavaLocale()));
      }
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  private void checkExitsAndRightsForTargetIdStockexhchange(Integer idStockexchange, User user) {
    Optional<Stockexchange> stockexchangeOpt = stockexchangeJpaRepository.findById(idStockexchange);
    if (stockexchangeOpt.isPresent()) {
      if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, stockexchangeOpt.get())) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
    } else {
      throw new NoSuchElementException(messageSource.getMessage("entity.not.found", new Object[] { idStockexchange },
          user.createAndGetJavaLocale()));
    }
  }

}
