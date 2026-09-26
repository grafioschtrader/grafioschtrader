package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.TenantAccess;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.security.UserAuthentication;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TenantAccessLevel;
import grafioschtrader.entities.AlgoEventLog;
import grafioschtrader.entities.AlgoSimulationResult;
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
import grafioschtrader.rest.TenantResource;
import grafioschtrader.service.AlgoReplayRunRegistry;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.AlgoSimulationRunStatus;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Committed deletion and rollback checks; sweeps only this fixture before and after each test. */
@GTIntegrationTestContext
class SimulationAccountDeletionIntegrationTest {
  private static final String PREFIX = "Account cleanup ";
  private static final LocalDate DATE = LocalDate.of(2020, 6, 15);
  private static final List<String> TENANT_TABLES = List.of("tenant", "portfolio", "securitycashaccount", "transaction",
      "watchlist", "algo_simulation_result", "algo_event_log", "hold_cashaccount_balance", "hold_cashaccount_deposit");

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private TransactionTemplate tx;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private TenantResource resource;
  @Autowired
  private HoldCashaccountBalanceJpaRepository balances;
  @Autowired
  private HoldCashaccountDepositJpaRepository deposits;
  @Autowired
  private AlgoReplayRunRegistry registry;
  @MockitoSpyBean
  private SimulationCleanupRepository cleanup;

  private User owner;
  private User advisor;
  private Integer home;
  private Integer advisorHome;
  private Integer topId;
  private Integer sharedId;
  private final List<Integer> environments = new ArrayList<>();
  private final List<Integer> privateIds = new ArrayList<>();

  @BeforeEach
  void fixture() {
    sweep();
    environments.clear();
    privateIds.clear();
    tx.executeWithoutResult(_ -> {
      home = tenant("home", null).getId();
      advisorHome = tenant("advisor", null).getId();
      owner = user("owner", home);
      advisor = user("advisor", advisorHome);
      login(owner);
      sharedId = instrument(null, "shared").getId();
      Watchlist watchlist = watchlist(home, "home", List.of(em.find(Security.class, sharedId)));
      AlgoTop top = new AlgoTop();
      top.setIdTenant(home);
      top.setName(PREFIX + "strategy");
      top.setPercentage(100f);
      top.setIdWatchlist(watchlist.getId());
      em.persist(top);
      topId = top.getId();
      ledger(home, "home", false);
      ledger(advisorHome, "advisor", false);
      for (int index = 0; index < 2; index++) {
        seedEnvironment(index);
      }
      em.flush();
      em.clear();
      for (Integer id : List.of(home, advisorHome, environments.get(0), environments.get(1))) {
        balances.createCashaccountBalanceEntireByTenant(id);
        deposits.createCashaccountDepositTimeFrameByTenant(id);
      }
    });
    login(owner);
  }

  @AfterEach
  void cleanUp() {
    reset(cleanup);
    SecurityContextHolder.clearContext();
    sweep();
  }

  @Test
  @DisplayName("Self-account deletion commits removal of both environments and their private data")
  void deleteAccountWithTwoEnvironments() throws Exception {
    tenants.deleteMyDataAndUserAccount();
    assertFamilyDeleted();
    assertAdvisorPreserved();
    assertThat(jdbc.queryForObject("SELECT created_by FROM securitycurrency WHERE id_securitycurrency = ?",
        Integer.class, sharedId)).isEqualTo(BaseConstants.SYSTEM_ID_USER);
  }

  @Test
  @DisplayName("Managed-client deletion uses the client's family and preserves the advisor")
  void deleteManagedClient() throws Exception {
    manageClient();
    assertThat(resource.deleteManagedClient(home).getStatusCode().value()).isEqualTo(204);
    assertFamilyDeleted();
    assertAdvisorPreserved();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_access WHERE id_tenant = ?", Integer.class, home))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("A failure after cleaning the first child rolls back both deletion entry points")
  void childCleanupFailureRollsBack(boolean managed) {
    if (managed)
      manageClient();
    doAnswer(invocation -> {
      invocation.callRealMethod();
      throw new CleanupFailure();
    }).when(cleanup).deleteTenantData(anyInt());

    assertThatThrownBy(() -> delete(managed)).isInstanceOf(CleanupFailure.class)
        .hasMessage("injected after child cleanup");
    assertFamilyIntact();
    assertAdvisorPreserved();
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("An active or cancelling second environment prevents any committed account deletion")
  void activeReplayRejectsDeletion(boolean managed) {
    if (managed)
      manageClient();
    assertThat(registry.reserve(environments.get(1))).isTrue();
    registry.cancel(environments.get(1));
    assertThatThrownBy(() -> delete(managed)).isInstanceOf(DataViolationException.class)
        .satisfies(error -> assertThat(((DataViolationException) error).getDataViolation()).anySatisfy(
            violation -> assertThat(violation.getMessageKey()).isEqualTo("gt.simulation.delete.run.active")));
    assertFamilyIntact();
    assertAdvisorPreserved();
  }

  @Test
  @DisplayName("The personal ZIP includes home and shared data while excluding environment-only data")
  void exportExcludesEnvironments() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    tenants.getExportPersonalDataAsZip(response);
    String data = null;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (entry.getName().equals("gt_data.sql"))
          data = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    assertThat(data).isNotNull().contains(PREFIX + "home", PREFIX + "shared", PREFIX + "strategy")
        .doesNotContain(PREFIX + "env", PREFIX + "private", PREFIX + "run", PREFIX + "advisor");
    assertFamilyIntact();
  }

  @Test
  @DisplayName("The home lock is held through child cleanup until the outer deletion transaction ends")
  void homeLockSurvivesChildCleanup() throws Exception {
    CountDownLatch childCleaned = new CountDownLatch(1);
    CountDownLatch allowDeletion = new CountDownLatch(1);
    CountDownLatch contenderStarted = new CountDownLatch(1);
    CountDownLatch contenderFinished = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicReference<Boolean> homeExists = new AtomicReference<>();
    doAnswer(invocation -> {
      invocation.callRealMethod();
      childCleaned.countDown();
      if (!allowDeletion.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("deletion wait timed out");
      return null;
    }).when(cleanup).deleteTenantData(anyInt());
    Thread deleting = new Thread(() -> {
      login(owner);
      try {
        tenants.deleteMyDataAndUserAccount();
      } catch (Throwable error) {
        failure.set(error);
      } finally {
        SecurityContextHolder.clearContext();
      }
    });
    Thread contender = new Thread(() -> {
      try {
        tx.executeWithoutResult(_ -> {
          contenderStarted.countDown();
          homeExists.set(tenants.lockMonitoringTenant(home).isPresent());
        });
      } catch (Throwable error) {
        failure.compareAndSet(null, error);
      } finally {
        contenderFinished.countDown();
      }
    });
    deleting.start();
    try {
      assertThat(childCleaned.await(10, TimeUnit.SECONDS)).isTrue();
      contender.start();
      assertThat(contenderStarted.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(contenderFinished.await(250, TimeUnit.MILLISECONDS)).isFalse();
    } finally {
      allowDeletion.countDown();
      deleting.join(15_000);
      contender.join(15_000);
    }
    assertThat(deleting.isAlive()).isFalse();
    assertThat(contender.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
    assertThat(homeExists.get()).isFalse();
    assertFamilyDeleted();
  }

  private void delete(boolean managed) throws Exception {
    if (managed)
      resource.deleteManagedClient(home);
    else
      tenants.deleteMyDataAndUserAccount();
  }

  private void manageClient() {
    tx.executeWithoutResult(_ -> {
      em.find(User.class, owner.getIdUser()).setHomeTenantReadOnly(true);
      em.persist(new TenantAccess(advisor.getIdUser(), home, TenantAccessLevel.MANAGE));
    });
    login(advisor);
  }

  private void assertFamilyDeleted() {
    for (Integer id : List.of(home, environments.get(0), environments.get(1))) {
      for (String table : TENANT_TABLES)
        assertCount(table, "id_tenant", id, 0);
    }
    assertCount("tenant", "id_parent_tenant", home, 0);
    assertCount("user", "id_user", owner.getIdUser(), 0);
    assertCount("algo_top_asset_security", "id_algo_assetclass_security", topId, 0);
    for (Integer id : privateIds) {
      assertCount("securitycurrency", "id_securitycurrency", id, 0);
      assertCount("historyquote", "id_securitycurrency", id, 0);
    }
    for (Integer id : environments)
      assertTasks(id, 0);
    assertCount("securitycurrency", "id_securitycurrency", sharedId, 1);
  }

  private void assertFamilyIntact() {
    assertCount("tenant", "id_tenant", home, 1);
    assertCount("user", "id_user", owner.getIdUser(), 1);
    assertCount("algo_top_asset_security", "id_algo_assetclass_security", topId, 1);
    for (Integer id : environments) {
      for (String table : TENANT_TABLES)
        assertCount(table, "id_tenant", id, 1);
      assertTasks(id, 1);
    }
    for (Integer id : privateIds) {
      assertCount("securitycurrency", "id_securitycurrency", id, 1);
      assertCount("historyquote", "id_securitycurrency", id, 1);
    }
  }

  private void assertAdvisorPreserved() {
    assertCount("tenant", "id_tenant", advisorHome, 1);
    assertCount("user", "id_user", advisor.getIdUser(), 1);
    assertCount("transaction", "id_tenant", advisorHome, 1);
  }

  private void assertCount(String table, String key, Integer id, int expected) {
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + key + " = ?", Integer.class, id))
        .as("%s.%s = %s", table, key, id).isEqualTo(expected);
  }

  private void assertTasks(Integer id, int expected) {
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_data_change WHERE entity = 'Tenant' AND id_entity = ?",
        Integer.class, id)).isEqualTo(expected);
  }

  private void seedEnvironment(int index) {
    Tenant environment = tenant("env " + index, home);
    environment.setIdAlgoTop(topId);
    Integer id = environment.getId();
    environments.add(id);
    Security security = instrument(id, "private " + index);
    privateIds.add(security.getId());
    watchlist(id, "env " + index, List.of(security, em.find(Security.class, sharedId)));
    ledger(id, "env " + index, true);
    AlgoSimulationResult result = new AlgoSimulationResult();
    result.setIdTenant(id);
    result.setIdAlgoTop(topId);
    result.setOpeningDate(DATE);
    result.setEndDate(DATE.plusDays(1));
    result.setStatus(AlgoSimulationRunStatus.COMPLETED);
    result.setStartedAt(DATE.atStartOfDay());
    result.setConventions("fixture");
    em.persist(result);
    AlgoEventLog event = new AlgoEventLog();
    event.setIdTenant(id);
    event.setIdSimulationResult(result.getId());
    event.setEventDate(DATE);
    event.setEventType(AlgoEventType.RUN_END);
    event.setRationale(PREFIX + "run " + index);
    em.persist(event);
    em.persist(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusDays(1), id, "Tenant"));
  }

  private Tenant tenant(String suffix, Integer parent) {
    Tenant tenant = new Tenant(PREFIX + suffix, "CHF", 0,
        parent == null ? TenantKindType.MAIN : TenantKindType.SIMULATION_COPY, false);
    tenant.setIdParentTenant(parent);
    em.persist(tenant);
    return tenant;
  }

  private User user(String suffix, Integer tenant) {
    User user = new User("account-sim-" + suffix + "@test.local", "unused", PREFIX + suffix, "en", 0);
    user.setIdTenant(tenant);
    em.persist(user);
    return user;
  }

  private Watchlist watchlist(Integer tenant, String suffix, List<Security> securities) {
    Watchlist watchlist = new Watchlist(tenant, PREFIX + suffix);
    watchlist.setSecuritycurrencyList(new ArrayList<>(securities));
    em.persist(watchlist);
    return watchlist;
  }

  private void ledger(Integer tenant, String suffix, boolean opening) {
    Portfolio portfolio = new Portfolio(tenant, PREFIX + suffix, "CHF");
    em.persist(portfolio);
    Cashaccount cash = new Cashaccount(PREFIX + suffix, 0.0, "CHF", portfolio);
    cash.setIdTenant(tenant);
    em.persist(cash);
    Transaction deposit = new Transaction(cash, 100.0, TransactionType.DEPOSIT, DATE.atStartOfDay());
    deposit.setIdTenant(tenant);
    deposit.setSimulationOpening(opening);
    em.persist(deposit);
  }

  private Security instrument(Integer ownerTenant, String suffix) {
    Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).setMaxResults(1)
        .getSingleResult();
    Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
        .getSingleResult();
    Security security = new Security(PREFIX + suffix, "CHF", assetclass, exchange, DATE.minusYears(1),
        DATE.plusYears(20), null, null, null);
    security.setLeverageFactor(1f);
    security.setIdTenantPrivate(ownerTenant);
    em.persist(security);
    em.flush();
    jdbc.update("INSERT INTO historyquote (id_securitycurrency, date, close) VALUES (?, ?, 100)", security.getId(),
        DATE);
    return security;
  }

  private void login(User user) {
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  private void sweep() {
    tx.executeWithoutResult(_ -> {
      List<Integer> ids = jdbc.queryForList(
          "SELECT id_tenant FROM tenant WHERE tenant_name LIKE ? ORDER BY id_tenant DESC", Integer.class, PREFIX + "%");
      for (Integer id : ids) {
        registry.release(id);
        cleanup.deleteTenantData(id);
        jdbc.update("DELETE FROM user WHERE id_tenant = ?", id);
        jdbc.update("DELETE FROM tenant WHERE id_tenant = ?", id);
      }
      for (Integer id : jdbc.queryForList("SELECT id_securitycurrency FROM security WHERE name LIKE ?", Integer.class,
          PREFIX + "%")) {
        jdbc.update("DELETE FROM security WHERE id_securitycurrency = ?", id);
        jdbc.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", id);
      }
      em.clear();
    });
  }

  private static class CleanupFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    CleanupFailure() {
      super("injected after child cleanup");
    }
  }
}
