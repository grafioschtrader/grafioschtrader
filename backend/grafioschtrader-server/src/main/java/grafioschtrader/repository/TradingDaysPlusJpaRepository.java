package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.TradingDaysPlus;

public interface TradingDaysPlusJpaRepository
    extends JpaRepository<TradingDaysPlus, LocalDate>, TradingDaysPlusJpaRepositoryCustom {

  long countByTradingDateBetween(LocalDate tradingDadeStart, LocalDate tradingDateEnd);

  List<TradingDaysPlus> findByTradingDateGreaterThanEqual(LocalDate tradingDate);

  /**
   * Returns possible trading days which includes the fromDate and toDate.
   *
   * @param fromDate
   * @param toDate
   * @return
   */
  List<TradingDaysPlus> findByTradingDateBetweenOrderByTradingDate(LocalDate fromDate, LocalDate toDate);

  List<TradingDaysPlus> findByTradingDateBetweenOrderByTradingDateDesc(LocalDate fromDate, LocalDate toDate);

  /**
   * Get global holidays from 2001-01-01 until now. Normally first of first of
   * January and December 25 for each year.
   *
   * @return
   */
  @Query(nativeQuery = true)
  Set<Date> getGlobalHolidays();
}
