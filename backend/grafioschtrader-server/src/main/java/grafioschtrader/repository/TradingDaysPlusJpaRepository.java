package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafioschtrader.dto.IStockexchangeTradingDate;
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
   * Returns the latest possible trading day strictly before the given date. Passing today yields the last completed
   * trading day, which is how a date can be tested for "lies in the past" without counting weekends and worldwide
   * holidays as a delay.
   *
   * @param tradingDate the exclusive upper bound
   * @return the newest trading day before the given date, or null when there is none
   */
  TradingDaysPlus findTopByTradingDateLessThanOrderByTradingDateDesc(LocalDate tradingDate);

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

  //@formatter:off
  /**
   * Returns the trading sessions the given stock exchanges really hold in a period, newest first per exchange.
   * <p>
   * A session is a day of the worldwide calendar {@code trading_days_plus} - which holds weekdays without global
   * holidays - that the exchange does not close in {@code trading_days_minus}. An exchange without public quotes
   * ({@code no_market_value}), which is how privately held papers are modelled, contributes no session at all and is
   * therefore absent from the result.
   * </p>
   * <p>
   * The caller measures the age of a price by counting the returned dates that lie after it. Calendar days must not be
   * used for that, or a weekend and every holiday of the exchange would count as a delay.
   * </p>
   *
   * Named query: TradingDaysPlus.getSessionsByStockexchange
   *
   * @param idsStockexchange the exchanges to examine; never empty
   * @param fromDate         first day of the period, inclusive
   * @param toDate           last day of the period, inclusive
   * @return the sessions grouped by exchange, within one exchange ordered by date descending
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<IStockexchangeTradingDate> getSessionsByStockexchange(@Param("idsStockexchange") List<Integer> idsStockexchange,
      @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
