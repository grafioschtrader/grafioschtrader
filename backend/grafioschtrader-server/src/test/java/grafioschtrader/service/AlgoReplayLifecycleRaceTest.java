package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.entities.AlgoSimulationResult;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.repository.AlgoSimulationResultJpaRepository;
import grafioschtrader.repository.SimulationCleanupRepository;
import grafioschtrader.repository.SimulationTenantService;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.AlgoSimulationRunStatus;
import grafioschtrader.types.SimulationInitializationMode;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The lifecycle rules of a replay, under the concurrency they exist for.
 *
 * <p>
 * Deliberately <b>not</b> {@code @Transactional}. Every rule here is about what a second caller observes, and a caller
 * observes only what has been committed - inside one rolled back transaction the races this class is about cannot occur
 * at all. That is also why {@code AlgoHistoricalReplayIntegrationTest} drives {@code prepare} and {@code execute} by
 * hand and never exercises {@code submit}, the reservation or the executor; this class covers what that one
 * structurally cannot.
 * </p>
 *
 * <p>
 * The replay executor is replaced by a mock, so a submitted run is accepted, recorded and reserved but never actually
 * walks its days. That is exactly the window the rules govern, and it makes each test a few hundred milliseconds
 * instead of a real replay.
 * </p>
 *
 * <p>
 * Because it commits, it cleans up after itself twice: once before each test, so a run that died halfway does not break
 * the retry, and once after. Both sweeps go through {@link SimulationCleanupRepository}, the same tenant-scoped delete
 * the application uses.
 * </p>
 */
@GTIntegrationTestContext
class AlgoReplayLifecycleRaceTest {

  /** Every tenant this class creates carries it, so a sweep can recognise its leftovers. */
  private static final String PREFIX = "Replay race";

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private TransactionTemplate tx;
  @Autowired
  private AlgoHistoricalReplayService replay;
  @Autowired
  private AlgoReplayRunRegistry registry;
  @Autowired
  private SimulationRunActivityService runActivity;
  @Autowired
  private SimulationTenantService simulations;
  @Autowired
  private SimulationCleanupRepository cleanup;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private AlgoSimulationResultJpaRepository results;

  /** Accepts the run without executing it, so the environment stays reserved for the duration of a test. */
  @MockitoBean(name = "algoReplayExecutor")
  private org.springframework.core.task.TaskExecutor executor;

  private final LocalDate opening = LocalDate.of(2020, 6, 15);
  private final LocalDate end = LocalDate.of(2020, 6, 30);
  private User user;
  private Integer homeIdTenant;
  private Integer environmentIdTenant;

  @BeforeEach
  void fixture() {
    sweepLeftovers();
    tx.executeWithoutResult(_ -> {
      Tenant home = new Tenant(PREFIX + " home", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(home);
      homeIdTenant = home.getId();
      Portfolio portfolio = new Portfolio(homeIdTenant, PREFIX + " portfolio", "CHF");
      em.persist(portfolio);
      Cashaccount cash = new Cashaccount(PREFIX + " CHF", 0.0, "CHF", portfolio);
      cash.setIdTenant(homeIdTenant);
      em.persist(cash);
      Transaction deposit = new Transaction(cash, 100000.0, TransactionType.DEPOSIT, opening.atTime(23, 59));
      deposit.setIdTenant(homeIdTenant);
      em.persist(deposit);
      Watchlist watchlist = new Watchlist(homeIdTenant, PREFIX + " universe");
      watchlist.setSecuritycurrencyList(new ArrayList<>());
      em.persist(watchlist);
      AlgoTop top = new AlgoTop();
      top.setIdTenant(homeIdTenant);
      top.setName(PREFIX + " strategy");
      top.setPercentage(100f);
      top.setIdWatchlist(watchlist.getId());
      em.persist(top);
      // The trading calendar is written by a scheduled task rather than seeded, so without this the run would span
      // no day and prepare would refuse it.
      for (LocalDate date = opening; !date.isAfter(end); date = date.plusDays(1)) {
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
            && em.find(TradingDaysPlus.class, date) == null) {
          em.persist(new TradingDaysPlus(date));
        }
      }
      em.flush();

      user = new User(homeIdTenant);
      user.setIdUser(0);
      login();
      SimulationTenantCreateDTO dto = new SimulationTenantCreateDTO();
      dto.setIdAlgoTop(top.getId());
      dto.setTenantName(PREFIX + " env");
      dto.setInitializationMode(SimulationInitializationMode.MANUAL_CASH);
      dto.setSimulationStartDate(opening);
      dto.setCashBalances(Map.of(cash.getId(), 100000.0));
      try {
        environmentIdTenant = simulations.createSimulationTenant(dto).getId();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    login();
  }

  @AfterEach
  void cleanUp() {
    if (environmentIdTenant != null) {
      registry.release(environmentIdTenant);
    }
    removeTenants(environmentIdTenant, homeIdTenant);
    environmentIdTenant = null;
    homeIdTenant = null;
    SecurityContextHolder.clearContext();
  }

  /** Puts the request into the home tenant, which is where a replay is submitted from. */
  private void login() {
    var authentication = new UsernamePasswordAuthenticationToken(PREFIX, "", List.of());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /** Removes what an earlier run of this class committed, recognising it by the tenant name. */
  private void sweepLeftovers() {
    List<Integer> stale = tx.execute(
        _ -> em.createQuery("SELECT t.idTenant FROM Tenant t WHERE t.tenantName LIKE ?1 ORDER BY t.idTenant DESC",
            Integer.class).setParameter(1, PREFIX + "%").getResultList());
    if (stale != null && !stale.isEmpty()) {
      removeTenants(stale.toArray(new Integer[0]));
    }
  }

  /** Children before parents, through the same tenant-scoped delete the application uses. */
  private void removeTenants(Integer... idTenants) {
    for (Integer idTenant : idTenants) {
      if (idTenant == null) {
        continue;
      }
      tx.executeWithoutResult(_ -> {
        cleanup.deleteTenantData(idTenant);
        em.clear();
        tenants.findById(idTenant).ifPresent(tenants::delete);
      });
    }
  }

  private grafioschtrader.algo.SimulationRunRequestDTO request() {
    var request = new grafioschtrader.algo.SimulationRunRequestDTO();
    request.setEndDate(end);
    return request;
  }

  @Test
  @DisplayName("Two submits of one environment leave exactly one accepted run")
  void concurrentSubmitsAcceptExactlyOne() throws Exception {
    CountDownLatch bothReady = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger accepted = new AtomicInteger();
    AtomicReference<Throwable> refusal = new AtomicReference<>();

    Runnable submit = () -> {
      login();
      bothReady.countDown();
      try {
        go.await(10, TimeUnit.SECONDS);
        replay.submit(environmentIdTenant, request());
        accepted.incrementAndGet();
      } catch (Throwable t) {
        refusal.compareAndSet(null, t);
      } finally {
        SecurityContextHolder.clearContext();
      }
    };
    Thread one = new Thread(submit, "race-submit-1");
    Thread two = new Thread(submit, "race-submit-2");
    one.start();
    two.start();
    assertThat(bothReady.await(10, TimeUnit.SECONDS)).isTrue();
    go.countDown();
    one.join(30_000);
    two.join(30_000);

    // Exactly one of the two got through; the other was told the environment is already replaying.
    assertThat(accepted.get()).as("accepted submits").isEqualTo(1);
    assertThat(refusal.get()).as("one of the two submits must be refused").isNotNull();
    assertThat(violationKeys(refusal.get())).contains("gt.simulation.run.already.running");
    List<AlgoSimulationResult> runs = results.findAll().stream()
        .filter(r -> environmentIdTenant.equals(r.getIdTenant())).toList();
    assertThat(runs).singleElement().extracting(AlgoSimulationResult::getStatus)
        .isEqualTo(AlgoSimulationRunStatus.RUNNING);
    assertThat(registry.isReserved(environmentIdTenant)).isTrue();
  }

  @Test
  @DisplayName("Reading the run right after it was accepted does not declare it interrupted")
  void statusInsideTheSubmitWindowDoesNotInterruptTheRun() {
    AlgoSimulationResult accepted = replay.submit(environmentIdTenant, request());
    assertThat(accepted.getStatus()).isEqualTo(AlgoSimulationRunStatus.RUNNING);

    // The mocked executor never ran the task, so this is exactly the window between recording a run and its worker
    // producing anything. Before the reservation was taken ahead of the commit, this read rewrote the row.
    var observed = replay.status(environmentIdTenant).orElseThrow();
    assertThat(observed.getStatus()).isEqualTo(AlgoSimulationRunStatus.RUNNING);
    assertThat(results.findByIdTenant(environmentIdTenant).orElseThrow().getStatus())
        .isEqualTo(AlgoSimulationRunStatus.RUNNING);
  }

  @Test
  @DisplayName("An environment being replayed is neither deletable nor enterable, and recovers when the run ends")
  void activeEnvironmentIsProtectedUntilTheWorkerHasStopped() {
    replay.submit(environmentIdTenant, request());
    assertThat(runActivity.isActive(environmentIdTenant)).isTrue();

    assertThatThrownBy(() -> simulations.deleteSimulationTenant(environmentIdTenant))
        .isInstanceOf(DataViolationException.class)
        .satisfies(e -> assertThat(violationKeys(e)).contains("gt.simulation.delete.run.active"));
    assertThat(tenants.findById(environmentIdTenant)).as("a refused deletion leaves the environment").isPresent();

    // Asking to cancel does not make it available: the worker may still be evaluating the day it is on.
    replay.cancel(environmentIdTenant);
    assertThat(runActivity.isActive(environmentIdTenant)).isTrue();
    assertThatThrownBy(() -> simulations.deleteSimulationTenant(environmentIdTenant))
        .isInstanceOf(DataViolationException.class);

    // Only when the worker has stopped and its final state is committed does the environment come back.
    registry.release(environmentIdTenant);
    assertThat(runActivity.isActive(environmentIdTenant)).isFalse();
    simulations.deleteSimulationTenant(environmentIdTenant);
    assertThat(tenants.findById(environmentIdTenant)).isEmpty();
    environmentIdTenant = null;
  }

  @Test
  @DisplayName("A run left running by a lost worker is reconciled rather than blocking the environment forever")
  void abandonedRunDoesNotKeepTheEnvironmentActive() {
    replay.submit(environmentIdTenant, request());
    // The server stopping is exactly this: the row says RUNNING, the registry knows nothing.
    registry.release(environmentIdTenant);

    assertThat(runActivity.isActive(environmentIdTenant)).isFalse();
    assertThat(results.findByIdTenant(environmentIdTenant).orElseThrow().getStatus())
        .isEqualTo(AlgoSimulationRunStatus.INTERRUPTED);
  }

  private List<String> violationKeys(Throwable failure) {
    if (failure instanceof DataViolationException violation) {
      return violation.getDataViolation().stream().map(grafiosch.exceptions.DataViolation::getMessageKey).toList();
    }
    return List.of();
  }
}
