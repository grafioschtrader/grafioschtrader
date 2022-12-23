package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.CopyTradingDaysFromSourceToTarget;
import grafioschtrader.dto.TradingDaysWithDateBoundaries;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.User;
import jakarta.transaction.Transactional;

public class TradingDaysMinusJpaRepositoryImpl implements TradingDaysMinusJpaRepositoryCustom {

  @Autowired
  StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @Autowired
  private MessageSource messageSource;

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
    for (AddRemoveDay addRemoveDay : saveTradingDays.addRemoveDays) {
      if (addRemoveDay.add) {
        createTradingDaysMinusList.add(new TradingDaysMinus(idStockexchange, addRemoveDay.date));
      } else {
        deleteTradingDaysMinusList.add(new TradingDaysMinus(idStockexchange, addRemoveDay.date));
      }
    }
    tradingDaysMinusJpaRepository.saveAll(createTradingDaysMinusList);
    tradingDaysMinusJpaRepository.deleteAllInBatch(deleteTradingDaysMinusList);

    return getTradingDaysByIdStockexchangeAndYear(idStockexchange, saveTradingDays.year);
  }

  @Override
  public TradingDaysWithDateBoundaries copyTradingDaysMinusToOtherStockexchange(
      CopyTradingDaysFromSourceToTarget ctdfstt, boolean excludeCheck) {
    if (!excludeCheck) {
      checkCopyTradingDaysMinusToOtherStockexchange(ctdfstt);
    }
    LocalDate dateFrom = ctdfstt.fullCopy ? LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY)
        : LocalDate.of(ctdfstt.returnOrCopyYear, 1, 1);
    LocalDate dateTo = ctdfstt.fullCopy ? LocalDate.parse(GlobalConstants.YOUNGEST_TRADING_CALENDAR_DAY)
        : LocalDate.of(ctdfstt.returnOrCopyYear, 12, 31);
    tradingDaysMinusJpaRepository.copyTradingMinusToOtherStockexchange(ctdfstt.sourceIdStockexchange,
        ctdfstt.targetIdStockexchange, dateFrom, dateTo);
    return getTradingDaysByIdStockexchangeAndYear(ctdfstt.targetIdStockexchange, ctdfstt.returnOrCopyYear);
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
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

  private void checkExitsAndRightsForTargetIdStockexhchange(Integer idStockexchange, User user) {
    Optional<Stockexchange> stockexchangeOpt = stockexchangeJpaRepository.findById(idStockexchange);
    if (stockexchangeOpt.isPresent()) {
      if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, stockexchangeOpt.get())) {
        throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
      }
    } else {
      throw new NoSuchElementException(messageSource.getMessage("entity.not.found", new Object[] { idStockexchange },
          user.createAndGetJavaLocale()));
    }
  }

}
