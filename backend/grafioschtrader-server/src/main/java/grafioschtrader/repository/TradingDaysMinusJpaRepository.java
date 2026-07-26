package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingDaysMinus.TradingDaysMinusKey;

public interface TradingDaysMinusJpaRepository
    extends JpaRepository<TradingDaysMinus, TradingDaysMinusKey>, TradingDaysMinusJpaRepositoryCustom {

  List<TradingDaysMinus> findByTradingDaysMinusKey_IdStockexchangeAndTradingDaysMinusKey_TradingDateMinusBetween(
      Integer idStockexchange, LocalDate fromDate, LocalDate toDate);

  /**
   * Deletes the derived non trading days of a stock exchange in the given date range, leaving the user created
   * entries ({@code create_type = 5}) untouched. Used by the rule based calendar generator before it re-inserts the
   * closures for the range, so a manually added or removed day always wins over a rule.
   *
   * @param idStockexchange the stock exchange whose derived rows are removed
   * @param fromDate        first date of the range, inclusive
   * @param toDate          last date of the range, inclusive
   */
  @Transactional
  @Modifying
  @Query("""
      DELETE FROM TradingDaysMinus t
      WHERE t.tradingDaysMinusKey.idStockexchange = ?1
        AND t.createType <> 5
        AND t.tradingDaysMinusKey.tradingDateMinus BETWEEN ?2 AND ?3""")
  void deleteDerivedByStockexchangeInRange(Integer idStockexchange, LocalDate fromDate, LocalDate toDate);

  /**
   * Deletes every derived non trading day of a stock exchange, leaving its user created entries
   * ({@code create_type = 5}) intact. Used when an exchange loses its calendar source, so no stale rule or index
   * derived calendar lingers.
   *
   * @param idStockexchange the stock exchange whose derived rows are removed
   */
  @Transactional
  @Modifying
  @Query("""
      DELETE FROM TradingDaysMinus t
      WHERE t.tradingDaysMinusKey.idStockexchange = ?1 AND t.createType <> 5""")
  void deleteDerivedByStockexchange(Integer idStockexchange);

  /**
   * Returns the dates in the given range on which the stock exchange has a user created non trading day
   * ({@code create_type = 5}). The rule based generator skips these dates so it never collides with a manual entry.
   *
   * @param idStockexchange the stock exchange to inspect
   * @param fromDate        first date of the range, inclusive
   * @param toDate          last date of the range, inclusive
   * @return the user created non trading days in the range
   */
  @Query("""
      SELECT t.tradingDaysMinusKey.tradingDateMinus FROM TradingDaysMinus t
      WHERE t.tradingDaysMinusKey.idStockexchange = ?1
        AND t.createType = 5
        AND t.tradingDaysMinusKey.tradingDateMinus BETWEEN ?2 AND ?3""")
  Set<LocalDate> findUserDatesInRange(Integer idStockexchange, LocalDate fromDate, LocalDate toDate);

  @Procedure(procedureName = "copyTradingMinusToOtherStockexchange")
  void copyTradingMinusToOtherStockexchange(Integer idSource, Integer idTarget, LocalDate dateFrom, LocalDate dateTo);

  /**
   * Derives the trading calendar of the index linked stock exchanges from the history quotes of the index in
   * {@code stockexchange.id_index_upd_calendar}. Every trading day of {@code trading_days_plus} for which the index
   * has no quote becomes a non trading day in {@code trading_days_minus}.
   *
   * @param idStockexchange limits the run to a single stock exchange, {@code null} processes every index linked stock
   *                        exchange
   * @param fullRebuild     {@code false} only appends the days after the newest manually created entry
   *                        ({@code create_type = 5}), which is the cheap weekly update. {@code true} discards all
   *                        connector created entries and derives the calendar over the full range of the index
   *                        history, which is required after the index history was wiped and reloaded. Manually
   *                        created entries survive a full rebuild.
   */
  @Procedure(procedureName = "updCalendarStockexchangeByIndex")
  void updCalendarStockexchangeByIndex(Integer idStockexchange, boolean fullRebuild);

}
