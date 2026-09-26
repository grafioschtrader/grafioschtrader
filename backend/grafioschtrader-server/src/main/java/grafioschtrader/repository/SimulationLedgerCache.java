package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SimulationSourceRepository.LedgerFingerprint;

/**
 * Keeps the ledger of one simulation environment in memory for the duration of a replay, so that the many reads of a
 * replay day no longer materialise every transaction and its instrument again.
 *
 * <p>
 * A replay reads the whole ledger several times per trading day — valuation, income entitlement, terminal events,
 * mean-reversion checks — and every step runs in its own database transaction, so nothing carried over from one read to
 * the next. The ledger only ever grows during a replay, which makes a cheap catch-up possible: each read first compares
 * row count and highest id with the database and, when they differ, fetches only the rows written since.
 * </p>
 *
 * <p>
 * The fingerprint check is what keeps the cache exact without hooking into the write paths. A rolled-back booking, a
 * deleted row or a row that became visible with a lower id than one already held all leave count or highest id out of
 * step with the cached rows, and then the whole ledger is read again. What the check cannot see is an existing row
 * whose columns change; a replay only appends to its ledger, and the transfer write path that connects two new rows
 * does so on the managed instances before any read can happen.
 * </p>
 *
 * <p>
 * The cache is bound to the replay thread and to the tenant of the environment. Reads of any other tenant, and reads on
 * any other thread, go to the database as before. Returned lists are immutable snapshots, but the transactions in them
 * are shared between callers and must be treated as read-only.
 * </p>
 */
public final class SimulationLedgerCache {

  private static final ThreadLocal<SimulationLedgerCache> ACTIVE = new ThreadLocal<>();

  /** The order of the named query SimulationSourceRepository.loadTransactions. */
  private static final Comparator<Transaction> LEDGER_ORDER = Comparator.comparing(Transaction::getTransactionDate)
      .thenComparing(Transaction::getTransactionTime).thenComparing(Transaction::getIdTransaction);

  private final Integer idTenant;
  private final List<Transaction> rows = new ArrayList<>();
  private Integer maxIdTransaction;

  private SimulationLedgerCache(Integer idTenant) {
    this.idTenant = idTenant;
  }

  /**
   * Opens the cache for a replay of the given environment on the calling thread.
   *
   * @param idTenant the simulation environment being replayed
   * @return the scope that has to be closed when the replay ends, successfully or not
   */
  public static Scope open(Integer idTenant) {
    ACTIVE.set(new SimulationLedgerCache(idTenant));
    return ACTIVE::remove;
  }

  /**
   * Returns the cache of the replay running on the calling thread when it belongs to the given tenant.
   *
   * @param idTenant the tenant whose ledger is about to be read
   * @return the cache, or null when the read has to go to the database
   */
  static SimulationLedgerCache active(Integer idTenant) {
    SimulationLedgerCache cache = ACTIVE.get();
    return cache != null && cache.idTenant.equals(idTenant) ? cache : null;
  }

  /**
   * Brings the cache up to date with the database and returns the transactions dated before {@code exclusiveEnd}.
   *
   * @param exclusiveEnd the first transaction date that is no longer included
   * @param source       the repository the rows and the fingerprint are read from
   * @return an immutable snapshot in ledger order
   */
  List<Transaction> transactions(LocalDate exclusiveEnd, SimulationSourceRepository source) {
    refresh(source);
    int end = firstIndexOnOrAfter(exclusiveEnd);
    return List.copyOf(rows.subList(0, end));
  }

  private void refresh(SimulationSourceRepository source) {
    LedgerFingerprint fingerprint = source.ledgerFingerprint(idTenant);
    if (matches(fingerprint)) {
      return;
    }
    boolean onlyAppended = fingerprint.getRowCount() > rows.size() && fingerprint.getMaxIdTransaction() != null
        && (maxIdTransaction == null || fingerprint.getMaxIdTransaction() > maxIdTransaction);
    if (onlyAppended) {
      append(source.loadTransactionsAfterId(idTenant, maxIdTransaction == null ? 0 : maxIdTransaction));
    }
    if (!onlyAppended || rows.size() != fingerprint.getRowCount()) {
      rows.clear();
      maxIdTransaction = null;
      append(source.loadTransactionsAfterId(idTenant, 0));
    }
  }

  private boolean matches(LedgerFingerprint fingerprint) {
    return fingerprint.getRowCount() == rows.size()
        && Objects.equals(fingerprint.getMaxIdTransaction(), maxIdTransaction);
  }

  private void append(List<Transaction> fresh) {
    if (fresh.isEmpty()) {
      return;
    }
    rows.addAll(fresh);
    rows.sort(LEDGER_ORDER);
    for (Transaction transaction : fresh) {
      if (maxIdTransaction == null || transaction.getIdTransaction() > maxIdTransaction) {
        maxIdTransaction = transaction.getIdTransaction();
      }
    }
  }

  /** Binary search over the date-ordered rows for the first one dated on or after the given day. */
  private int firstIndexOnOrAfter(LocalDate date) {
    int low = 0, high = rows.size();
    while (low < high) {
      int mid = (low + high) >>> 1;
      if (rows.get(mid).getTransactionDate().isBefore(date)) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }

  /** Closes the cache of the calling thread; the replay reads from the database again afterwards. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
