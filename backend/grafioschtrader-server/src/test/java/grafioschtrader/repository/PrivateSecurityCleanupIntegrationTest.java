package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.security.UserAuthentication;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Exercises both real deletion entry points with an isolated graph that rolls back after each test. */
@GTIntegrationTestContext
@Transactional
class PrivateSecurityCleanupIntegrationTest {
  private static final LocalDate DATE = LocalDate.of(2020, 6, 15);
  private static final List<String> INSTRUMENT_TABLES = List.of("security", "securitycurrency", "historyquote",
      "historyquote_legacy", "historyquote_period", "securitysplit", "dividend");
  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private SimulationTenantService simulations;
  @Autowired
  private TenantJpaRepository tenants;
  private Tenant home;
  private Tenant other;
  private User user;
  private Security shared;
  private Security foreignPrivate;

  @BeforeEach
  void fixture() {
    home = tenant("Private cleanup home", TenantKindType.MAIN);
    other = tenant("Private cleanup other", TenantKindType.MAIN);
    user = new User("private-cleanup@test.local", "unused", "Private cleanup", "en", 0);
    user.setIdTenant(home.getId());
    em.persist(user);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
    shared = instrument(null, "Cleanup shared");
    foreignPrivate = instrument(other.getId(), "Cleanup foreign private");
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 2 })
  @DisplayName("Environment deletion removes private instruments, prices and pending tasks while preserving home data")
  void deleteEnvironment(int privateCount) {
    Security homePrivate = instrument(home.getId(), "Cleanup home private");
    Watchlist homeWatchlist = watchlist(home, List.of(homePrivate, shared));
    AlgoTop top = new AlgoTop();
    top.setIdTenant(home.getId());
    top.setName("Cleanup home strategy");
    top.setPercentage(100f);
    top.setIdWatchlist(homeWatchlist.getId());
    em.persist(top);
    Tenant environment = tenant("Private cleanup sim", TenantKindType.SIMULATION_COPY);
    environment.setIdParentTenant(home.getId());
    environment.setIdAlgoTop(top.getId());
    List<Security> privateInstruments = privateInstruments(environment, privateCount);
    Watchlist environmentWatchlist = watchlist(environment, privateInstruments);
    Portfolio portfolio = new Portfolio(environment.getId(), "Cleanup portfolio", "CHF");
    em.persist(portfolio);
    Cashaccount cash = new Cashaccount("Cleanup CHF", 0.0, "CHF", portfolio);
    cash.setIdTenant(environment.getId());
    em.persist(cash);
    // The rollback-only fixture needs no holdings rebuild; the production deletion must remove this ledger too.
    Transaction deposit = new Transaction(cash, 100.0, TransactionType.DEPOSIT, DATE.atStartOfDay());
    deposit.setIdTenant(environment.getId());
    deposit.setSimulationOpening(true);
    em.persist(deposit);
    em.persist(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusDays(1), environment.getId(), "Tenant"));
    flushAndClear();

    simulations.deleteSimulationTenant(environment.getId());
    flushAndClear();

    assertThat(em.find(Tenant.class, environment.getId())).isNull();
    assertThat(em.find(Portfolio.class, portfolio.getId())).isNull();
    assertThat(em.find(Cashaccount.class, cash.getId())).isNull();
    assertThat(em.find(Transaction.class, deposit.getId())).isNull();
    assertThat(em.find(Watchlist.class, environmentWatchlist.getId())).isNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_sec_cur WHERE id_watchlist = ?", Integer.class,
        environmentWatchlist.getId())).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_data_change WHERE entity = 'Tenant' AND id_entity = ?",
        Integer.class, environment.getId())).isZero();
    privateInstruments.forEach(security -> assertInstrumentRows(security, 0));
    assertInstrumentRows(homePrivate, 1);
    assertThat(em.find(Tenant.class, home.getId())).isNotNull();
    assertThat(em.find(AlgoTop.class, top.getId())).isNotNull();
    assertThat(em.find(Watchlist.class, homeWatchlist.getId()).getSecuritycurrencyList()).hasSize(2);
    assertPreservedInstruments();
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 2 })
  @DisplayName("Account deletion removes private parent rows and history, preserving shared instruments and their tasks")
  void deleteAccount(int privateCount) throws Exception {
    List<Security> privateInstruments = privateInstruments(home, privateCount);
    List<Security> members = new ArrayList<>(privateInstruments);
    members.add(shared);
    watchlist(home, members);
    flushAndClear();

    tenants.deleteMyDataAndUserAccount();
    flushAndClear();

    assertThat(em.find(Tenant.class, home.getId())).isNull();
    assertThat(em.find(User.class, user.getIdUser())).isNull();
    privateInstruments.forEach(security -> assertInstrumentRows(security, 0));
    assertPreservedInstruments();
    assertThat(jdbc.queryForObject("SELECT created_by FROM securitycurrency WHERE id_securitycurrency = ?",
        Integer.class, shared.getId())).isEqualTo(BaseConstants.SYSTEM_ID_USER);
  }

  private Tenant tenant(String name, TenantKindType kind) {
    Tenant tenant = new Tenant(name, "CHF", 0, kind, false);
    em.persist(tenant);
    return tenant;
  }

  private List<Security> privateInstruments(Tenant tenant, int count) {
    List<Security> instruments = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      instruments.add(instrument(tenant.getId(), "Cleanup private " + i));
    }
    return instruments;
  }

  private Watchlist watchlist(Tenant tenant, List<Security> instruments) {
    Watchlist watchlist = new Watchlist(tenant.getId(), "Cleanup watchlist");
    watchlist.setSecuritycurrencyList(new ArrayList<>(instruments));
    em.persist(watchlist);
    return watchlist;
  }

  /** Seeds every child relevant to the joined-table deletion, without invoking external price connectors. */
  private Security instrument(Integer owner, String name) {
    Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
        .getSingleResult();
    Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
        .getSingleResult();
    Security security = new Security(name, "CHF", assetclass, exchange, DATE.minusYears(1), DATE.plusYears(20), null,
        null, null);
    security.setLeverageFactor(1f);
    security.setIdTenantPrivate(owner);
    em.persist(security);
    em.flush();
    Integer id = security.getId();
    jdbc.update("INSERT INTO historyquote (id_securitycurrency, date, close) VALUES (?, ?, 100)", id, DATE);
    jdbc.update("""
        INSERT INTO historyquote_legacy (id_securitycurrency, transfer_date, date, close) VALUES (?, ?, ?, 90)
        """, id, DATE, DATE.minusDays(1));
    jdbc.update("""
        INSERT INTO historyquote_period (id_securitycurrency, from_date, to_date, price, create_type)
        VALUES (?, ?, ?, 95, 0)
        """, id, DATE.minusDays(2), DATE);
    jdbc.update("""
        INSERT INTO securitysplit (id_securitycurrency, split_date, from_factor, to_factor, create_type)
        VALUES (?, ?, 1, 2, 0)
        """, id, DATE);
    jdbc.update("""
        INSERT INTO dividend (id_securitycurrency, ex_date, amount, amount_adjusted, currency, create_type)
        VALUES (?, ?, 1, 1, 'CHF', 0)
        """, id, DATE);
    for (TaskTypeExtended task : List.of(TaskTypeExtended.SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA,
        TaskTypeExtended.SECURITY_SPLIT_UPDATE_FOR_SECURITY, TaskTypeExtended.SECURITY_DIVIDEND_UPDATE_FOR_SECURITY)) {
      em.persist(
          new TaskDataChange(task, TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusDays(1), id, "Security"));
    }
    return security;
  }

  private void assertPreservedInstruments() {
    assertInstrumentRows(shared, 1);
    assertInstrumentRows(foreignPrivate, 1);
    assertThat(em.find(Tenant.class, other.getId())).isNotNull();
  }

  private void assertInstrumentRows(Security security, int expected) {
    for (String table : INSTRUMENT_TABLES) {
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id_securitycurrency = ?", Integer.class,
          security.getId())).as("%s rows for %s", table, security.getName()).isEqualTo(expected);
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_data_change WHERE entity = 'Security' AND id_entity = ?",
        Integer.class, security.getId())).as("pending tasks for %s", security.getName()).isEqualTo(3 * expected);
  }

  private void flushAndClear() {
    em.flush();
    em.clear();
  }
}
