package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetHistoryquote;

/**
 * Repository for GTNetHistoryquote entities.
 *
 * Provides access to historical (EOD) price data for FOREIGN instruments in the GTNet pool.
 * This table is only used for instruments that do NOT exist in the local database
 * (no matching entry exists in security/currencypair table when JOINed).
 *
 * For local instruments, historical data is stored in the standard {@link grafioschtrader.entities.Historyquote}
 * table instead.
 */
public interface GTNetHistoryquoteJpaRepository extends JpaRepository<GTNetHistoryquote, Integer> {

  /**
   * Finds a historical quote by instrument and date.
   *
   * @param idGtNetInstrument the instrument ID
   * @param date the trading date
   * @return the quote if found
   */
  Optional<GTNetHistoryquote> findByGtNetInstrumentIdGtNetInstrumentAndDate(Integer idGtNetInstrument, Date date);

  /**
   * Finds all historical quotes for a specific instrument.
   *
   * @param idGtNetInstrument the instrument ID
   * @return list of historical quotes, ordered by date descending
   */
  @Query("SELECT hq FROM GTNetHistoryquote hq WHERE hq.gtNetInstrument.idGtNetInstrument = ?1 ORDER BY hq.date DESC")
  List<GTNetHistoryquote> findByInstrumentId(Integer idGtNetInstrument);

  /**
   * Finds historical quotes for an instrument within a date range.
   *
   * @param idGtNetInstrument the instrument ID
   * @param fromDate the start date (inclusive)
   * @param toDate the end date (inclusive)
   * @return list of historical quotes within the range, ordered by date ascending
   */
  @Query("""
      SELECT hq FROM GTNetHistoryquote hq
      WHERE hq.gtNetInstrument.idGtNetInstrument = ?1
        AND hq.date >= ?2
        AND hq.date <= ?3
      ORDER BY hq.date ASC""")
  List<GTNetHistoryquote> findByInstrumentIdAndDateRange(Integer idGtNetInstrument, Date fromDate, Date toDate);

  /**
   * Finds all historical quotes for a list of instrument IDs.
   *
   * @param instrumentIds list of instrument IDs
   * @return list of historical quotes
   */
  List<GTNetHistoryquote> findByGtNetInstrumentIdGtNetInstrumentIn(List<Integer> instrumentIds);

  /**
   * Batch query for historical quotes across multiple foreign instruments.
   * Returns all quotes for the given instrument IDs where the date is greater than or equal to the fromDate.
   * Results are ordered by instrument ID and date for efficient grouping.
   *
   * @param instrumentIds list of GTNet instrument IDs to query
   * @param fromDate the minimum date (inclusive) for quotes
   * @return list of GTNetHistoryquotes ordered by instrument ID and date ascending
   */
  @Query("""
      SELECT hq FROM GTNetHistoryquote hq
      WHERE hq.gtNetInstrument.idGtNetInstrument IN :ids
        AND hq.date >= :fromDate
      ORDER BY hq.gtNetInstrument.idGtNetInstrument, hq.date""")
  List<GTNetHistoryquote> findByInstrumentIdsAndDateGreaterThanEqual(
      @Param("ids") List<Integer> instrumentIds, @Param("fromDate") Date fromDate);

  /**
   * Finds the earliest date with historical data for an instrument.
   *
   * @param idGtNetInstrument the instrument ID
   * @return the earliest date, or null if no data exists
   */
  @Query("SELECT MIN(hq.date) FROM GTNetHistoryquote hq WHERE hq.gtNetInstrument.idGtNetInstrument = ?1")
  Date findEarliestDateByInstrumentId(Integer idGtNetInstrument);

  /**
   * Finds the latest date with historical data for an instrument.
   *
   * @param idGtNetInstrument the instrument ID
   * @return the latest date, or null if no data exists
   */
  @Query("SELECT MAX(hq.date) FROM GTNetHistoryquote hq WHERE hq.gtNetInstrument.idGtNetInstrument = ?1")
  Date findLatestDateByInstrumentId(Integer idGtNetInstrument);

  /**
   * Counts the number of historical quotes for an instrument.
   *
   * @param idGtNetInstrument the instrument ID
   * @return the count of quotes
   */
  long countByGtNetInstrumentIdGtNetInstrument(Integer idGtNetInstrument);

  /**
   * Counts the number of historical quotes for an instrument within a date range.
   *
   * @param idGtNetInstrument the instrument ID
   * @param fromDate the start date (inclusive)
   * @param toDate the end date (inclusive)
   * @return the count of quotes in the range
   */
  @Query("""
      SELECT COUNT(hq) FROM GTNetHistoryquote hq
      WHERE hq.gtNetInstrument.idGtNetInstrument = ?1
        AND hq.date >= ?2
        AND hq.date <= ?3""")
  long countByInstrumentIdAndDateRange(Integer idGtNetInstrument, Date fromDate, Date toDate);

  /**
   * Deletes all historical quotes for a specific instrument.
   *
   * @param idGtNetInstrument the instrument ID
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM GTNetHistoryquote hq WHERE hq.gtNetInstrument.idGtNetInstrument = ?1")
  void deleteByInstrumentId(Integer idGtNetInstrument);

  /**
   * Deletes historical quotes for an instrument within a date range.
   *
   * @param idGtNetInstrument the instrument ID
   * @param fromDate the start date (inclusive)
   * @param toDate the end date (inclusive)
   */
  @Transactional
  @Modifying
  @Query("""
      DELETE FROM GTNetHistoryquote hq
      WHERE hq.gtNetInstrument.idGtNetInstrument = ?1
        AND hq.date >= ?2
        AND hq.date <= ?3""")
  void deleteByInstrumentIdAndDateRange(Integer idGtNetInstrument, Date fromDate, Date toDate);

  /**
   * Finds all dates that have historical data for an instrument.
   * Useful for determining gaps in historical data coverage.
   *
   * @param idGtNetInstrument the instrument ID
   * @return list of dates with data, ordered ascending
   */
  @Query("SELECT hq.date FROM GTNetHistoryquote hq WHERE hq.gtNetInstrument.idGtNetInstrument = ?1 ORDER BY hq.date")
  List<Date> findExistingDatesByInstrumentId(Integer idGtNetInstrument);

}
