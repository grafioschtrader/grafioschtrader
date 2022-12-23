package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.TradingDaysWithDateBoundaries;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.User;
import jakarta.transaction.Transactional;

public class TradingDaysPlusJpaRepositoryImpl implements TradingDaysPlusJpaRepositoryCustom {

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  private Map<LocalDate, Long> numberOfTradingDaysYesterdayNowMap = new ConcurrentHashMap<>();
  private LocalDate untilYesterday = null;

  @Override
  public boolean hasTradingDayBetweenUntilYesterday(LocalDate tradingDay) {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    Long numberOfTradingDays = null;
    if (untilYesterday == null || !yesterday.equals(untilYesterday)) {
      // Map is out dated
      untilYesterday = yesterday;
      numberOfTradingDaysYesterdayNowMap.clear();
    }
    numberOfTradingDays = numberOfTradingDaysYesterdayNowMap.get(tradingDay);
    if (numberOfTradingDays == null) {
      numberOfTradingDays = tradingDaysPlusJpaRepository.countByTradingDateBetween(tradingDay, yesterday);
      numberOfTradingDaysYesterdayNowMap.put(tradingDay, numberOfTradingDays);
    }
    return numberOfTradingDays != 0;
  }

  @Override
  @Transactional()
  public TradingDaysWithDateBoundaries getTradingDaysByYear(int year) {
    LocalDate fromDate = LocalDate.of(year, 1, 1);
    LocalDate toDate = LocalDate.of(year, 12, 31);
    return new TradingDaysWithDateBoundaries(
        tradingDaysPlusJpaRepository.findByTradingDateBetweenOrderByTradingDate(fromDate, toDate).stream()
            .map(TradingDaysPlus::getTradingDate).collect(Collectors.toList()),
        null);
  }

  @Override
  public TradingDaysWithDateBoundaries save(SaveTradingDays saveTradingDays) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.isAdmin(user)) {
      List<TradingDaysPlus> createTradingDaysPlusList = new ArrayList<>();
      List<TradingDaysPlus> deleteTradingDaysPlusList = new ArrayList<>();
      for (AddRemoveDay addRemoveDay : saveTradingDays.addRemoveDays) {
        if (addRemoveDay.add) {
          createTradingDaysPlusList.add(new TradingDaysPlus(addRemoveDay.date));
        } else {
          deleteTradingDaysPlusList.add(new TradingDaysPlus(addRemoveDay.date));
        }
      }
      untilYesterday = null;
      numberOfTradingDaysYesterdayNowMap.clear();
      tradingDaysPlusJpaRepository.saveAll(createTradingDaysPlusList);
      tradingDaysPlusJpaRepository.deleteAllInBatch(deleteTradingDaysPlusList);
      return getTradingDaysByYear(saveTradingDays.year);
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

  }

}
