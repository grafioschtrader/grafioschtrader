package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.entities.Transaction;

/** Ledger read of {@link SimulationSourceRepository} that a running replay serves from memory. */
public interface SimulationSourceRepositoryCustom {

  /**
   * Returns the transactions of a tenant dated before {@code exclusiveEnd}, ordered by date, time and id. On the thread
   * of a replay of that tenant the rows come from {@link SimulationLedgerCache}; everywhere else they are read from the
   * database. The result is the same in both cases and must not be modified.
   *
   * @param idTenant     the tenant whose transactions are read
   * @param exclusiveEnd the first transaction date that is no longer included
   * @return the transactions, with instrument and cash account loaded
   */
  List<Transaction> transactions(Integer idTenant, LocalDate exclusiveEnd);
}
