package grafioschtrader.reports;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.EntityLimit;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.EntityLimitService;
import grafioschtrader.entities.*;
import grafioschtrader.exportdelete.MyDataExportDeleteDefinition;
import grafioschtrader.repository.*;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.*;
import jakarta.persistence.*;

/** Real MariaDB persistence, tenant boundaries, locking and export selections; no repository mocks. */
@GTIntegrationTestContext
class WatchlistIndicatorIntegrationTest {
  @Autowired
  WatchlistIndicatorColumnJpaRepository columns;
  @Autowired
  HistoryquoteJpaRepository history;
  @Autowired
  WatchlistReport report;
  @Autowired
  EntityLimitService limits;
  @Autowired
  PlatformTransactionManager transactions;
  @PersistenceContext
  EntityManager em;
  @Autowired
  private CopyTenantService copyTenant;
  private TransactionTemplate tx;
  private Integer tenant, other, watchlist, otherWatchlist, limitId, previousLimit;
  private User user;

  @BeforeEach
  void fixture() {
    tx = new TransactionTemplate(transactions);
    tx.executeWithoutResult(_ -> {
      var first = new Tenant("Indicator integration", "CHF", 0, TenantKindType.MAIN, false);
      var second = new Tenant("Indicator other", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(first);
      em.persist(second);
      tenant = first.getId();
      other = second.getId();
      var own = new Watchlist(tenant, "Indicator test");
      own.setLastTimestamp(LocalDateTime.now());
      em.persist(own);
      watchlist = own.getId();
      var foreign = new Watchlist(other, "Indicator foreign");
      em.persist(foreign);
      otherWatchlist = foreign.getId();
    });
    user = new User(tenant);
    user.setIdUser(0);
    user.setRoles(List.of());
    login();
    limits.evictUser(0);
  }

  @AfterEach
  void cleanup() {
    tx.executeWithoutResult(_ -> {
      em.createNativeQuery(
          "DELETE FROM user_entity_change_count WHERE id_user = 0 AND entity_name = 'WatchlistIndicatorColumn'")
          .executeUpdate();
      if (limitId != null)
        em.find(EntityLimit.class, limitId).setLimitValue(previousLimit);
      em.createQuery("DELETE FROM WatchlistIndicatorColumn c WHERE c.idTenant IN (?1)")
          .setParameter(1, List.of(tenant, other)).executeUpdate();
      em.createNativeQuery("DELETE FROM watchlist_sec_cur WHERE id_watchlist = ?1").setParameter(1, watchlist)
          .executeUpdate();
      em.createQuery("DELETE FROM Watchlist w WHERE w.idTenant IN (?1)").setParameter(1, List.of(tenant, other))
          .executeUpdate();
      em.createQuery("DELETE FROM Tenant t WHERE t.idTenant IN (?1)").setParameter(1, List.of(tenant, other))
          .executeUpdate();
    });
    limits.evictUser(0);
    SecurityContextHolder.clearContext();
  }

  private void login() {
    var authentication = new UsernamePasswordAuthenticationToken("indicator-test", "", List.of());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private WatchlistIndicatorColumn column(String expression) {
    var column = new WatchlistIndicatorColumn();
    column.setLabel("Test");
    column.setExpression(expression);
    return column;
  }

  private WatchlistIndicatorColumn save(WatchlistIndicatorColumn column) throws Exception {
    return columns.saveOnlyAttributes(column, null, Set.of());
  }

  @Test
  @DisplayName("Concurrent default creation commits exactly one complete set")
  void concurrentDefaults() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    Callable<Boolean> create = () -> {
      login();
      try {
        if (!start.await(10, TimeUnit.SECONDS))
          throw new IllegalStateException("Start timeout");
        columns.createDefaults();
        return true;
      } catch (DataViolationException e) {
        return false;
      } finally {
        SecurityContextHolder.clearContext();
      }
    };
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = executor.submit(create);
      var b = executor.submit(create);
      start.countDown();
      assertThat(a.get(20, TimeUnit.SECONDS)).isNotEqualTo(b.get(20, TimeUnit.SECONDS));
    }
    var stored = columns.findByIdTenantOrderByUiOrder(tenant);
    assertThat(stored).hasSize(10);
    assertThat(stored.get(3).getResultType()).isEqualTo(IndicatorResultType.BOOLEAN);
    var reversed = new ArrayList<>(stored.stream().map(WatchlistIndicatorColumn::getId).toList());
    Collections.reverse(reversed);
    assertThat(columns.reorder(reversed)).extracting(WatchlistIndicatorColumn::getId)
        .containsExactlyElementsOf(reversed);
    for (var row : stored)
      columns.delEntityWithTenant(row.getId(), tenant);
    assertThat(report.getWatchlistWithIndicators(watchlist).columns).isEmpty();
    assertThat(columns.countByIdTenant(tenant)).isZero();
  }

  @Test
  @DisplayName("A limit below ten rejects the whole default set")
  void lowLimitIsAtomic() {
    tx.executeWithoutResult(_ -> {
      var limit = em.createQuery(
          "SELECT e FROM EntityLimit e WHERE e.entityName = 'WatchlistIndicatorColumn' AND e.idUser IS NULL AND e.idRole IS NULL AND e.limitType = 0",
          EntityLimit.class).getSingleResult();
      limitId = limit.getId();
      previousLimit = limit.getLimitValue();
      limit.setLimitValue(5);
    });
    limits.evictUser(0);
    assertThatThrownBy(() -> columns.createDefaults()).isInstanceOf(DataViolationException.class);
    assertThat(columns.countByIdTenant(tenant)).isZero();
  }

  @Test
  @DisplayName("Ownership, static types and field constraints are enforced in the persistence path")
  void tenantAndValidation() throws Exception {
    var own = column("SMA(2) > SMA(3)");
    own.setIdWatchlist(watchlist);
    own.setResultType(IndicatorResultType.NUMBER);
    var saved = save(own);
    assertThat(saved.getResultType()).isEqualTo(IndicatorResultType.BOOLEAN);
    var invalid = column("CLOSE(0)");
    invalid.setIdWatchlist(otherWatchlist);
    assertThatThrownBy(() -> save(invalid)).isInstanceOf(DataViolationException.class);
    assertThatThrownBy(() -> save(column("RS(2)"))).isInstanceOf(DataViolationException.class);
    var marked = column("PRICE > 1");
    marked.setLowerMark(1.0);
    assertThatThrownBy(() -> save(marked)).isInstanceOf(DataViolationException.class);
    assertThatThrownBy(() -> columns.reorder(List.of(saved.getId(), saved.getId())))
        .isInstanceOf(DataViolationException.class);
    assertThatThrownBy(() -> report.getWatchlistWithIndicators(otherWatchlist)).isInstanceOf(SecurityException.class);
    user.setIdTenant(other);
    login();
    assertThatThrownBy(() -> save(saved)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> columns.delEntityWithTenant(saved.getId(), other)).isInstanceOf(SecurityException.class);
    assertThat(columns.findByIdTenantOrderByUiOrder(other)).isEmpty();
  }

  @Test
  @DisplayName("The complete report loads real series and dividends, isolates cell errors and keeps existing values stable")
  void reportCells() throws Exception {
    var ids = tx.execute(_ -> {
      var security = em.createQuery(
          "SELECT s FROM Security s WHERE s.idTenantPrivate IS NULL AND s.stockexchange.noMarketValue = false",
          Security.class).setMaxResults(1).getSingleResult();
      var pair = em.createQuery("SELECT c FROM Currencypair c", Currencypair.class).setMaxResults(1).getSingleResult();
      em.find(Watchlist.class, watchlist).setSecuritycurrencyList(new ArrayList<>(List.of(security, pair)));
      return List.of(security.getId(), pair.getId());
    });
    var defaults = columns.createDefaults();
    var zero = save(column("0"));
    var arithmetic = save(column("1 / 0"));
    var first = report.getWatchlistWithIndicators(watchlist);
    assertThat(first.cells.keySet()).containsExactlyInAnyOrderElementsOf(ids);
    for (var cells : first.cells.values()) {
      assertThat(cells.get(zero.getId()).value()).isEqualTo(0.0);
      assertThat(cells.get(arithmetic.getId()).status()).isEqualTo(IndicatorCellStatus.ARITHMETIC);
      assertThat(cells.values()).noneMatch(c -> c.status() == IndicatorCellStatus.ERROR);
    }
    assertThat(first.cells.get(ids.get(1)).get(defaults.getLast().getId()).status())
        .isEqualTo(IndicatorCellStatus.NOT_APPLICABLE);
    save(column("SMA(999)"));
    var second = report.getWatchlistWithIndicators(watchlist);
    ids.forEach(
        id -> first.cells.get(id).forEach((key, cell) -> assertThat(second.cells.get(id).get(key)).isEqualTo(cell)));
  }

  @Test
  @DisplayName("Demo copying preserves definitions and maps watchlist ownership")
  void copyDefinitions() throws Exception {
    var definition = column("CLOSE(2)");
    definition.setIdWatchlist(watchlist);
    definition.setLowerMark(5.0);
    definition.setUpperMark(10.0);
    definition.setMarkColors(IndicatorMarkColors.NEUTRAL);
    save(definition);
    var source = new User(tenant);
    source.setIdUser(-1001);
    var target = new User(other);
    target.setIdUser(-1002);
    copyTenant.copyTenant(source, target);
    var copied = columns.findByIdTenantOrderByUiOrder(other);
    assertThat(copied).hasSize(1);
    var row = copied.getFirst();
    assertThat(row.getId()).isNotEqualTo(definition.getId());
    assertThat(row.getIdWatchlist()).isNotEqualTo(watchlist);
    assertThat(row.getExpression()).isEqualTo(definition.getExpression());
    assertThat(row.getLowerMark()).isEqualTo(5.0);
    assertThat(row.getMarkColors()).isEqualTo(IndicatorMarkColors.NEUTRAL);
    tx.executeWithoutResult(
        _ -> assertThat(em.find(Watchlist.class, row.getIdWatchlist()).getIdTenant()).isEqualTo(other));
  }

  @Test
  @DisplayName("A benchmark outside every watchlist is included in export; projected series match alert rows")
  void exportAndSeries() throws Exception {
    Integer benchmark = tx.execute(_ -> em.createQuery("SELECT c.idSecuritycurrency FROM Currencypair c", Integer.class)
        .setMaxResults(1).getSingleResult());
    var column = column("RS(2)");
    column.setIdBenchmark(benchmark);
    save(column);
    tx.executeWithoutResult(_ -> {
      for (String table : List.of("currencypair", "securitycurrency")) {
        var definition = Arrays.stream(MyDataExportDeleteDefinition.exportDefinitions)
            .filter(d -> d.table.equals(table) && d.isExport() && d.sqlStatement != null
                && d.sqlStatement.contains("wic.id_benchmark")
                && (table.equals("currencypair") || d.sqlStatement.contains("sc.dtype = 'C'")))
            .findFirst().orElseThrow();
        String sql = "SELECT " + definition.sqlStatement;
        var query = em.createNativeQuery(sql, Object[].class);
        long count = sql.chars().filter(c -> c == '?').count();
        for (int i = 1; i <= count; i++)
          query.setParameter(i, tenant);
        assertThat(query.getResultList()).anyMatch(row -> ((Number) ((Object[]) row)[0]).intValue() == benchmark);
      }
    });
    var projected = history.findNewestDateCloseHighLowByIds(List.of(benchmark), LocalDate.now()).stream()
        .map(HistoryquoteJpaRepository.IndicatorQuote::of).toList();
    var alerts = history.findByIdSecuritycurrencyAndCreateTypeNotAndDateLessThanEqualOrderByDateDesc(benchmark,
        (byte) 1, LocalDate.now(), Limit.of(1200));
    assertThat(projected).extracting(p -> p.getDate())
        .containsExactlyElementsOf(alerts.reversed().stream().map(Historyquote::getDate).toList());
  }
}
