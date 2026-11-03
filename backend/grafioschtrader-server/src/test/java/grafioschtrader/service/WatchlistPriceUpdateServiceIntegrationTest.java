package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reports.WatchlistReport;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.service.GTNetLastpriceService.SecurityCurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Exercises real MariaDB row locks and snapshots; only the external price-fetching boundary is mocked. */
@GTIntegrationTestContext
class WatchlistPriceUpdateServiceIntegrationTest {

  @Autowired
  private WatchlistPriceUpdateService service;
  @Autowired
  private WatchlistJpaRepository watchlists;
  @Autowired
  private WatchlistReport report;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @PersistenceContext
  private EntityManager em;
  @MockitoBean
  private GTNetLastpriceService prices;

  private Integer idTenant;
  private Integer idWatchlist;
  private Integer idSecurity;
  private TransactionTemplate transaction;
  private final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

  @BeforeEach
  void createWatchlist() {
    transaction = new TransactionTemplate(transactionManager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    transaction.executeWithoutResult(_ -> {
      Tenant tenant = new Tenant("Watchlist claim test", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      idTenant = tenant.getId();
      Watchlist watchlist = new Watchlist(idTenant, "Watchlist claim test");
      em.persist(watchlist);
      idWatchlist = watchlist.getId();
    });
  }

  @AfterEach
  void removeWatchlist() {
    transaction.executeWithoutResult(_ -> {
      em.find(Watchlist.class, idWatchlist).getSecuritycurrencyList().clear();
      em.flush();
      em.createQuery("DELETE FROM Watchlist w WHERE w.idWatchlist = :id").setParameter("id", idWatchlist)
          .executeUpdate();
      if (idSecurity != null) {
        em.remove(em.find(Security.class, idSecurity));
      }
      em.createQuery("DELETE FROM Tenant t WHERE t.idTenant = :id").setParameter("id", idTenant).executeUpdate();
    });
  }

  @Test
  @DisplayName("Null and stale timestamps permit an update; fresh and threshold-equal timestamps do not")
  void respectsUpdateInterval() {
    assertThat(service.claimUpdate(idWatchlist, idTenant, now, now.minusMinutes(1)).needsPriceUpdate()).isTrue();
    var fresh = service.claimUpdate(idWatchlist, idTenant, now.plusSeconds(30), now.minusSeconds(30));
    assertThat(fresh.needsPriceUpdate()).isFalse();
    assertThat(fresh.lastTimestamp()).isEqualTo(now);
    assertThat(service.claimUpdate(idWatchlist, idTenant, now.plusMinutes(1), now).needsPriceUpdate()).isFalse();
    var stale = service.claimUpdate(idWatchlist, idTenant, now.plusSeconds(61), now.plusSeconds(1));
    assertThat(stale.needsPriceUpdate()).isTrue();
    assertThat(stale.lastTimestamp()).isEqualTo(now.plusSeconds(61));
  }

  @Test
  @DisplayName("Two simultaneous callers receive exactly one update reservation")
  void concurrentClaimsHaveOneWinner() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> {
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return service.claimUpdate(idWatchlist, idTenant, now, now.minusMinutes(1));
      });
      var second = executor.submit(() -> {
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return service.claimUpdate(idWatchlist, idTenant, now, now.minusMinutes(1));
      });
      start.countDown();
      var firstClaim = first.get(10, TimeUnit.SECONDS);
      var secondClaim = second.get(10, TimeUnit.SECONDS);
      assertThat(firstClaim.needsPriceUpdate()).isNotEqualTo(secondClaim.needsPriceUpdate());
      assertThat(firstClaim.lastTimestamp()).isEqualTo(now);
      assertThat(secondClaim.lastTimestamp()).isEqualTo(now);
    }
  }

  @Test
  @DisplayName("The claim timestamp stays current even after an older REPEATABLE READ snapshot was established")
  void readsCurrentTimestampDespiteOuterSnapshot() {
    transaction.executeWithoutResult(_ -> {
      assertThat(watchlists.findByIdWatchlistAndIdTenant(idWatchlist, idTenant).getLastTimestamp()).isNull();
      var claim = service.claimUpdate(idWatchlist, idTenant, now, now.minusMinutes(1));
      assertThat(claim.lastTimestamp()).isEqualTo(now);
      em.clear();
      assertThat(watchlists.findByIdWatchlistAndIdTenant(idWatchlist, idTenant).getLastTimestamp()).isNull();
      var loser = service.claimUpdate(idWatchlist, idTenant, now.plusSeconds(1), now.minusMinutes(1));
      assertThat(loser.needsPriceUpdate()).isFalse();
      assertThat(loser.lastTimestamp()).isEqualTo(now);
      assertThat(em.createNativeQuery("SELECT @@tx_isolation", String.class).getSingleResult())
          .isEqualTo("REPEATABLE-READ");
    });
    assertThat(storedTimestamp()).isEqualTo(now);
  }

  @Test
  @DisplayName("A foreign tenant cannot reserve an update or change its timestamp")
  void rejectsForeignTenant() {
    assertThatThrownBy(() -> service.claimUpdate(idWatchlist, -1, now, now.minusMinutes(1)))
        .isInstanceOf(SecurityException.class);
    assertThat(storedTimestamp()).isNull();
  }

  @Test
  @DisplayName("A failed report rolls back without undoing its committed reservation")
  void reservationSurvivesReportFailure() {
    assertThatThrownBy(() -> transaction.executeWithoutResult(_ -> {
      service.claimUpdate(idWatchlist, idTenant, now, now.minusMinutes(1));
      throw new IllegalStateException("Simulated report failure");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(storedTimestamp()).isEqualTo(now);
    assertThat(service.claimUpdate(idWatchlist, idTenant, now.plusSeconds(30), now.minusSeconds(30)).needsPriceUpdate())
        .isFalse();
    assertThat(service.claimUpdate(idWatchlist, idTenant, now.plusSeconds(61), now.plusSeconds(1)).needsPriceUpdate())
        .isTrue();
  }

  @Test
  @DisplayName("A second report finishes while the first price fetch is paused, with the committed response timestamp")
  void slowProviderDoesNotHoldWatchlistLock() throws Exception {
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch releaseProvider = new CountDownLatch(1);
    when(prices.updateLastpriceIncludeSupplier(anyList(), anyList(), anyList())).thenAnswer(_ -> {
      fetching.countDown();
      assertThat(releaseProvider.await(20, TimeUnit.SECONDS)).isTrue();
      return new SecurityCurrency(List.of(), List.of());
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> report.getWatchlistwithPeriodPerformance(idWatchlist, idTenant, 30, false));
      try {
        assertThat(fetching.await(10, TimeUnit.SECONDS)).isTrue();
        LocalDateTime reserved = storedTimestamp();
        assertThat(reserved).isNotNull();
        var second = executor.submit(() -> report.getWatchlistwithPeriodPerformance(idWatchlist, idTenant, 30, false));
        assertThat(second.get(10, TimeUnit.SECONDS).lastTimestamp).isEqualTo(reserved);
        assertThat(first.isDone()).isFalse();
        releaseProvider.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).lastTimestamp).isEqualTo(reserved);
        assertThat(storedTimestamp()).isEqualTo(reserved);
        verify(prices, times(1)).updateLastpriceIncludeSupplier(anyList(), anyList(), anyList());
      } finally {
        releaseProvider.countDown();
      }
    }
  }

  private LocalDateTime storedTimestamp() {
    return transaction.execute(_ -> watchlists.findByIdWatchlistAndIdTenant(idWatchlist, idTenant).getLastTimestamp());
  }

  @Test
  @DisplayName("Dividend-inclusive report does not flush instruments changed by the asynchronous price writer")
  void dividendsAfterAsyncPriceUpdate() {
    transaction.executeWithoutResult(_ -> {
      Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).getResultStream()
          .filter(a -> a.getCategoryType() == AssetclassType.EQUITIES
              && a.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT)
          .findFirst().orElseThrow();
      Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
          .getSingleResult();
      Security security = new Security("Watchlist dividend flush test", "CHF", assetclass, exchange,
          LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31), DistributionFrequency.DF_YEAR, null, null);
      security.setSLast(100.0);
      security.setLeverageFactor(1.0f);
      em.persist(security);
      idSecurity = security.getId();
      em.find(Watchlist.class, idWatchlist).setSecuritycurrencyList(List.of(security));
    });
    when(prices.updateLastpriceIncludeSupplier(anyList(), anyList(), anyList())).thenAnswer(invocation -> {
      List<Security> securities = invocation.getArgument(0);
      // The provider changes the request's instrument and commits its price in a separate persistence context.
      securities.getFirst().setSLast(110.0);
      securities.getFirst().setSTimestamp(now);
      transaction.executeWithoutResult(_ -> {
        Security stored = em.find(Security.class, idSecurity);
        stored.setSLast(110.0);
        stored.setSTimestamp(now);
      });
      return new SecurityCurrency(securities, List.of());
    });

    var result = report.getWatchlistwithPeriodPerformance(idWatchlist, idTenant, 30, true);
    assertThat(result.securityPositionList).hasSize(1);
    assertThat(result.securityPositionList.getFirst().securitycurrency.getSLast()).isEqualTo(110.0);
    Double storedPrice = transaction.execute(_ -> em.find(Security.class, idSecurity).getSLast());
    assertThat(storedPrice).isEqualTo(110.0);
  }
}
