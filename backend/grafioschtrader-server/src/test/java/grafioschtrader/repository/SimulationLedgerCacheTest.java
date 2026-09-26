package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SimulationSourceRepository.LedgerFingerprint;

class SimulationLedgerCacheTest {

  private static final int TENANT = 65;
  private static final LocalDate DAY = LocalDate.of(2024, 3, 4);

  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final List<Transaction> database = new ArrayList<>();
  private SimulationLedgerCache.Scope scope;

  @AfterEach
  void close() {
    if (scope != null) {
      scope.close();
    }
  }

  @Test
  @DisplayName("Only fills written after the last read are fetched again")
  void appendsNewRowsOnly() {
    open();
    insert(1, DAY);
    insert(2, DAY.plusDays(1));
    assertThat(ids(cache().transactions(DAY.plusDays(5), source))).containsExactly(1, 2);

    insert(3, DAY.plusDays(2));
    assertThat(ids(cache().transactions(DAY.plusDays(5), source))).containsExactly(1, 2, 3);

    verify(source).loadTransactionsAfterId(TENANT, 0);
    verify(source).loadTransactionsAfterId(TENANT, 2);
    verify(source, never()).loadTransactions(anyInt(), any());
  }

  @Test
  @DisplayName("An unchanged ledger is served without reading any row")
  void unchangedLedgerReadsNoRows() {
    open();
    insert(1, DAY);
    cache().transactions(DAY.plusDays(1), source);
    cache().transactions(DAY.plusDays(1), source);
    verify(source, times(1)).loadTransactionsAfterId(eq(TENANT), anyInt());
  }

  @Test
  @DisplayName("The exclusive end date filters like the database query")
  void exclusiveEndDate() {
    open();
    insert(5, DAY.plusDays(2));
    insert(4, DAY);
    insert(6, DAY.plusDays(1));
    assertThat(ids(cache().transactions(DAY, source))).isEmpty();
    assertThat(ids(cache().transactions(DAY.plusDays(2), source))).containsExactly(4, 6);
    assertThat(ids(cache().transactions(DAY.plusDays(3), source))).containsExactly(4, 6, 5);
  }

  @Test
  @DisplayName("A rolled-back fill forces a complete reload")
  void rollbackReloads() {
    open();
    insert(1, DAY);
    insert(2, DAY);
    cache().transactions(DAY.plusDays(1), source);

    database.removeIf(t -> t.getIdTransaction() == 2);
    insert(3, DAY);
    assertThat(ids(cache().transactions(DAY.plusDays(1), source))).containsExactly(1, 3);
    verify(source, times(2)).loadTransactionsAfterId(TENANT, 0);
  }

  @Test
  @DisplayName("Another tenant and a closed scope read from the database")
  void outsideTheReplay() {
    assertThat(SimulationLedgerCache.active(TENANT)).isNull();
    open();
    assertThat(SimulationLedgerCache.active(TENANT + 1)).isNull();
    scope.close();
    scope = null;
    assertThat(SimulationLedgerCache.active(TENANT)).isNull();
  }

  private void open() {
    scope = SimulationLedgerCache.open(TENANT);
    when(source.ledgerFingerprint(TENANT)).thenAnswer(_ -> fingerprint());
    when(source.loadTransactionsAfterId(eq(TENANT), anyInt())).thenAnswer(invocation -> {
      int after = invocation.getArgument(1);
      return database.stream().filter(t -> t.getIdTransaction() > after).toList();
    });
  }

  private SimulationLedgerCache cache() {
    return SimulationLedgerCache.active(TENANT);
  }

  private void insert(int id, LocalDate date) {
    Transaction transaction = new Transaction();
    transaction.setIdTransaction(id);
    transaction.setIdTenant(TENANT);
    transaction.setTransactionTime(date.atTime(12, 0));
    database.add(transaction);
  }

  private LedgerFingerprint fingerprint() {
    long count = database.size();
    Integer max = database.stream().map(Transaction::getIdTransaction).max(Integer::compare).orElse(null);
    return new LedgerFingerprint() {
      @Override
      public long getRowCount() {
        return count;
      }

      @Override
      public Integer getMaxIdTransaction() {
        return max;
      }
    };
  }

  private static List<Integer> ids(List<Transaction> transactions) {
    return transactions.stream().map(Transaction::getIdTransaction).toList();
  }
}
