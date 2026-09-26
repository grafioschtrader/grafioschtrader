package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import grafiosch.service.EntityLimitService;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** No enclosing test transaction: assertions reread the ledger after the worker has committed its failure. */
@GTIntegrationTestContext
@DisplayName("Replay transaction limit preserves committed partial results")
class AlgoReplayTransactionLimitCommitTest {
  private static final String PREFIX = "Replay limit commit";
  private static final LocalDate OPENING = LocalDate.of(2020, 6, 15);
  @PersistenceContext
  private EntityManager em;
  @Autowired
  private TransactionTemplate tx;
  @Autowired
  private SimulationTenantService simulations;
  @Autowired
  private SimulationCleanupRepository cleanup;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private AlgoHistoricalReplayService replay;
  @Autowired
  private AlgoSimulationResultJpaRepository results;
  @Autowired
  private SimulationSourceRepository source;
  @MockitoBean
  private EntityLimitService limits;
  private User user;
  private Integer environmentId;

  @BeforeEach
  void fixture() {
    sweep();
    tx.executeWithoutResult(_ -> {
      Tenant home = new Tenant(PREFIX + " home", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(home);
      user = new User(home.getId());
      user.setIdUser(0);
      login();
      Portfolio portfolio = new Portfolio(home.getId(), PREFIX, "CHF");
      em.persist(portfolio);
      Cashaccount cash = new Cashaccount(PREFIX, 0.0, "CHF", portfolio);
      cash.setIdTenant(home.getId());
      em.persist(cash);
      Watchlist watchlist = new Watchlist(home.getId(), PREFIX);
      watchlist.setSecuritycurrencyList(new ArrayList<>());
      em.persist(watchlist);
      AlgoTop top = new AlgoTop();
      top.setIdTenant(home.getId());
      top.setName(PREFIX);
      top.setPercentage(100f);
      top.setIdWatchlist(watchlist.getId());
      em.persist(top);
      AlgoAssetclass bucket = new AlgoAssetclass();
      bucket.setIdTenant(home.getId());
      bucket.setIdAlgoAssetclassParent(top.getId());
      bucket.setPercentage(100f);
      bucket.setName(PREFIX);
      em.persist(bucket);
      em.flush();
      SimulationTenantCreateDTO request = new SimulationTenantCreateDTO();
      request.setIdAlgoTop(top.getId());
      request.setTenantName(PREFIX + " env");
      request.setInitializationMode(SimulationInitializationMode.MANUAL_CASH);
      request.setSimulationStartDate(OPENING);
      request.setCashBalances(Map.of(cash.getId(), 1000.0));
      try {
        environmentId = simulations.createSimulationTenant(request).getId();
      } catch (Exception failure) {
        throw new IllegalStateException(failure);
      }
      StandingOrderCashaccount order = new StandingOrderCashaccount();
      order.setIdTenant(environmentId);
      order.setCashaccount(source.cashaccounts(environmentId).getFirst());
      order.setCashaccountAmount(100.0);
      order.setTransactionType(TransactionType.DEPOSIT);
      order.setRepeatUnit(RepeatUnit.DAYS);
      order.setRepeatInterval((short) 1);
      order.setPeriodDayPosition(PeriodDayPosition.SPECIFIC_DAY);
      order.setWeekendAdjust(WeekendAdjustType.AFTER);
      order.setQuoteToleranceDays((byte) -3);
      order.setValidFrom(OPENING.plusDays(1));
      order.setValidTo(OPENING.plusDays(4));
      order.setNextExecutionDate(order.getValidFrom());
      em.persist(order);
    });
    when(limits.resolveForCurrentUser(LimitKeyConfig.KEY_TRANSACTION)).thenReturn(Optional.of(2));
  }

  @Test
  void earlierBookingsSurviveFailureAndLaterOccurrencesAreNeverWritten() {
    var prepared = tx.execute(_ -> replay.prepare(environmentId, OPENING.plusDays(4), user));
    replay.execute(environmentId, prepared.getId(), user);
    var result = results.findById(prepared.getId()).orElseThrow();
    assertThat(result.getStatus()).isEqualTo(AlgoSimulationRunStatus.RUN_FAILED);
    assertThat(result.getFailureMessage()).isEqualTo("gt.transaction.limit.exceeded");
    assertThat(result.getTotalReturn()).isNull();
    var ledger = source.transactions(environmentId, OPENING.plusDays(5));
    assertThat(ledger).hasSize(2);
    assertThat(ledger).filteredOn(Transaction::isSimulationOpening).hasSize(1);
    assertThat(ledger).filteredOn(transaction -> !transaction.isSimulationOpening()).singleElement()
        .satisfies(transaction -> {
          assertThat(transaction.getTransactionDate()).isEqualTo(OPENING.plusDays(1));
          assertThat(transaction.getCashaccountAmount()).isEqualTo(100.0);
        });
  }

  private void login() {
    var auth = new UsernamePasswordAuthenticationToken(PREFIX, "", List.of());
    auth.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void cleanUp() {
    try {
      sweep();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /** Also runs before setup so an interrupted test can be retried without resetting the test database. */
  private void sweep() {
    List<Integer> ids = tx.execute(
        _ -> em.createQuery("SELECT t.idTenant FROM Tenant t WHERE t.tenantName LIKE ?1 ORDER BY t.idTenant DESC",
            Integer.class).setParameter(1, PREFIX + "%").getResultList());
    for (Integer id : ids) {
      tx.executeWithoutResult(_ -> {
        cleanup.deleteTenantData(id);
        em.clear();
        tenants.findById(id).ifPresent(tenants::delete);
      });
    }
  }
}
