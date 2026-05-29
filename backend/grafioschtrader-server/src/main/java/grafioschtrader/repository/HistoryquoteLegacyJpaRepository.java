package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.IShadowRow;
import grafioschtrader.entities.HistoryquoteLegacy;
import jakarta.transaction.Transactional;

/**
 * Repository for the {@code historyquote_legacy} shadow archive. Besides the standard CRUD inherited from
 * {@link JpaRepository} (used for editing/deleting single archived rows through the propose-change flow), it owns the
 * native-SQL plumbing that maintains the archive and supplements the live {@link grafioschtrader.entities.Historyquote}
 * table from it. See {@link grafioschtrader.repository.SecurityJpaRepositoryImpl} for the merge logic. The archive
 * preserves historical prices the active feed connector can no longer supply (the security's listing has moved to
 * another exchange / data provider).
 */
public interface HistoryquoteLegacyJpaRepository extends JpaRepository<HistoryquoteLegacy, Integer>,
    HistoryquoteLegacyJpaRepositoryCustom, UpdateCreateJpaRepository<HistoryquoteLegacy> {

  /**
   * Returns every archived row for the security, regardless of merge status, as full entities so each row carries its
   * primary key for editing/deletion. Backs the {@code GET /historyquotelegacy/{idSec}} endpoint. Distinct from
   * {@link #findLegacyMissingInLive} which filters to the unmerged subset used by the merge flow.
   *
   * @param idSecuritycurrency the security or currencypair id
   * @return archived rows ordered by date descending
   */
  List<HistoryquoteLegacy> findByIdSecuritycurrencyOrderByDateDesc(Integer idSecuritycurrency);

  /**
   * Returns only the {@code date} values of every archived row for the security. Used by the CSV import path to
   * pre-fetch the set of already-archived dates in a single query so duplicates can be filtered client-side
   * before a batch {@code saveAll}, mirroring how {@code HistoryquoteImport} handles the live table.
   */
  @Query(value = "SELECT date FROM historyquote_legacy WHERE id_securitycurrency = ?1", nativeQuery = true)
  List<LocalDate> findDatesByIdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Copies every existing historyquote row of the given security into historyquote_legacy
   * with the supplied transfer_date as the per-row archival boundary, preserving the
   * row's create_type so its provenance (MANUAL_IMPORTED, ADD_MODIFIED_USER, etc.) survives
   * the round-trip. Synthetic FILL_GAP_BY_CONNECTOR rows (create_type = 6) are filtered
   * out — they're derived data with no provenance value, and archiving them would let
   * later supplement runs "promote" garbage values to real (CONNECTOR_CREATED) status.
   * Uses INSERT IGNORE so rows already archived in a prior transfer keep their older
   * transfer_date. Named query: HistoryquoteLegacy.copyLiveToLegacy.
   */
  @Modifying
  @Transactional
  @Query(nativeQuery = true)
  void copyLiveToLegacy(Integer idSecuritycurrency, LocalDate transferDate);

  /**
   * Returns archive rows the live historyquote table does not effectively cover. A live row
   * counts as "covering" the date only if its create_type is anything other than 6
   * (FILL_GAP_BY_CONNECTOR) — synthetic gap-fillers do not count as real coverage, so the
   * shadow row is returned and will overwrite the filler via insertLegacyIntoLive's
   * ON DUPLICATE KEY UPDATE. The caller is expected to apply any post-archival split factor
   * (Securitysplit.calcSplitFatorForFromDate using the row's transferDate) before inserting
   * them into live. Named query: HistoryquoteLegacy.findLegacyMissingInLive.
   */
  @Query(nativeQuery = true)
  List<IShadowRow> findLegacyMissingInLive(Integer idSecuritycurrency);

  /**
   * Inserts an adjusted shadow row into live, preserving the shadow's create_type so a
   * round-tripped MANUAL_IMPORTED (or ADD_MODIFIED_USER, etc.) row comes back labeled
   * with its original origin — not relabeled as CONNECTOR_CREATED. On duplicate
   * (id_securitycurrency, date) keys the existing live row is overwritten only if it
   * was a synthetic gap-filler (create_type = 6 = FILL_GAP_BY_CONNECTOR); real rows
   * (CONNECTOR_CREATED, MANUAL_IMPORTED, FILLED_CLOSED_LINEAR_TRADING_DAY, CALCULATED,
   * ADD_MODIFIED_USER) are preserved unchanged. On overwrite, the live row's create_type
   * is replaced by the shadow's create_type. Named query: HistoryquoteLegacy.insertLegacyIntoLive.
   */
  @Modifying
  @Transactional
  @Query(nativeQuery = true)
  int insertLegacyIntoLive(Integer idSecuritycurrency, LocalDate date, double close, Double open, Double high,
      Double low, Long volume, Byte createType);

  @Modifying
  @Transactional
  @Query(value = "DELETE FROM historyquote_legacy WHERE id_securitycurrency = ?1", nativeQuery = true)
  void deleteLegacyByIdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Bulk-import counterpart to copyLiveToLegacy: inserts a single row into historyquote_legacy
   * from CSV-parsed values. INSERT IGNORE so re-importing the same (id_securitycurrency, date)
   * pair preserves the existing row (with its older transfer_date / create_type). Used by
   * {@code HistoryquoteLegacyImport} backing the legacy view's "Import quotes" menu — the
   * round-trip counterpart of the legacy view's CSV export.
   *
   * @return 1 if the row was inserted, 0 if a row with the same unique key already existed
   */
  @Modifying
  @Transactional
  @Query(value = "INSERT IGNORE INTO historyquote_legacy "
      + "(id_securitycurrency, transfer_date, `date`, `close`, `open`, high, low, volume, create_type) "
      + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)", nativeQuery = true)
  int insertLegacyRow(Integer idSecuritycurrency, LocalDate transferDate, LocalDate date, double close, Double open,
      Double high, Double low, Long volume, Byte createType);

  /**
   * Total number of archived rows for a security, regardless of merge status. Drives the
   * "Show legacy" menu visibility on the live history-quote view: a value of 0 hides the menu
   * entry; any positive value shows it.
   */
  @Query(value = "SELECT COUNT(*) FROM historyquote_legacy WHERE id_securitycurrency = ?1", nativeQuery = true)
  Integer countLegacyForSecurity(Integer idSecuritycurrency);

  /**
   * Bulk in-place split adjustment on archived rows whose date is strictly before splitDate.
   * Mirrors the formula used by {@code SecurityJpaRepositoryImpl.supplementFromShadow}: OHLC
   * values are multiplied by {@code fromFactor/toFactor}, volume by {@code toFactor/fromFactor}.
   * A 2/1 split therefore halves OHLC and doubles volume; a 1/2 reverse split does the opposite.
   *
   * @param idSecuritycurrency the security whose archive should be adjusted
   * @param fromFactor pre-split factor (e.g. 1 for a 1/2 reverse split)
   * @param toFactor post-split factor (e.g. 2 for a 2/1 split)
   * @param splitDate only rows with {@code date < splitDate} are updated
   * @return the number of rows touched
   */
  @Modifying
  @Transactional
  @Query(value = "UPDATE historyquote_legacy SET "
      + "`close` = `close` * ?2 / ?3, "
      + "`open` = `open` * ?2 / ?3, "
      + "high = high * ?2 / ?3, "
      + "low = low * ?2 / ?3, "
      + "volume = ROUND(volume * ?3 / ?2) "
      + "WHERE id_securitycurrency = ?1 AND `date` < ?4", nativeQuery = true)
  int applySplitToLegacy(Integer idSecuritycurrency, int fromFactor, int toFactor, LocalDate splitDate);

}
