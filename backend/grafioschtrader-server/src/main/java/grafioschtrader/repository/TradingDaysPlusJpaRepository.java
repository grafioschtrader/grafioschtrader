package grafioschtrader.repository;

import java.time.LocalDate;
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
   * Returns the last possible trading day, which is the end of the period the rule based calendar generator fills up
   * to. Null only when {@code trading_days_plus} is empty.
   *
   * @return the latest trading day, or null when the table is empty
   */
  TradingDaysPlus findTopByOrderByTradingDateDesc();

  /**
   * Returns the first possible trading day, the start of a full rule based rebuild.
   *
   * @return the earliest trading day, or null when the table is empty
   */
  TradingDaysPlus findTopByOrderByTradingDateAsc();

  /**
   * Returns possible trading days which includes the fromDate and toDate.
   */
  List<TradingDaysPlus> findByTradingDateBetweenOrderByTradingDate(LocalDate fromDate, LocalDate toDate);

  List<TradingDaysPlus> findByTradingDateBetweenOrderByTradingDateDesc(LocalDate fromDate, LocalDate toDate);

  /**
   * Get global holidays from 2001-01-01 until now. Normally first of first of January and December 25 for each year.
   */
  @Query(nativeQuery = true)
  Set<LocalDate> getGlobalHolidays();
}
