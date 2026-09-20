package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Resolves an alert scope the way the scheduler does: with the fixture committed and no transaction of its own.
 *
 * <p>
 * Deliberately not annotated {@code @Transactional}. The scheduled evaluation runs outside any transaction, and while
 * the resolver walked the lazy {@code Watchlist.securitycurrencyList} that was fatal - every background run ended in a
 * {@code LazyInitializationException} and no alert was ever evaluated. A test that wrapped itself in a transaction
 * would have kept a session open and passed over exactly that defect, so this one commits its fixture and cleans it up
 * afterwards instead.
 * </p>
 */
@GTIntegrationTestContext
class AlgoAlertScopeResolverIntegrationTest {

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private AlgoAlertScopeResolver resolver;

  @Autowired
  private WatchlistJpaRepository watchlists;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private Integer tenantId;

  @AfterEach
  void removeFixture() {
    if (tenantId == null) {
      return;
    }
    inTransaction(() -> {
      em.createQuery("DELETE FROM AlgoTop t WHERE t.idTenant = ?1").setParameter(1, tenantId).executeUpdate();
      em.createNativeQuery("DELETE FROM watchlist_sec_cur WHERE id_watchlist IN "
          + "(SELECT id_watchlist FROM watchlist WHERE id_tenant = ?1)").setParameter(1, tenantId).executeUpdate();
      em.createQuery("DELETE FROM Watchlist w WHERE w.idTenant = ?1").setParameter(1, tenantId).executeUpdate();
      em.createQuery("DELETE FROM Tenant t WHERE t.idTenant = ?1").setParameter(1, tenantId).executeUpdate();
      return null;
    });
  }

  @Test
  void watchlistInstrumentsAreResolvedWithoutAnOpenSession() {
    Integer idWatchlist = inTransaction(() -> {
      Tenant tenant = new Tenant("Alert scope", "CHF", 0, TenantKindType.MAIN, false);
      em.persist(tenant);
      tenantId = tenant.getId();
      Security first = em.createQuery("SELECT s FROM Security s WHERE s.currency = 'CHF' ORDER BY s.idSecuritycurrency",
          Security.class).setMaxResults(1).getSingleResult();
      Watchlist watchlist = new Watchlist(tenantId, "Alert scope universe");
      watchlist.setSecuritycurrencyList(new ArrayList<>(List.of(first)));
      em.persist(watchlist);
      AlgoTop top = new AlgoTop();
      top.setIdTenant(tenantId);
      top.setName("Alert scope strategy");
      top.setPercentage(100f);
      top.setIdWatchlist(watchlist.getId());
      em.persist(top);
      return watchlist.getId();
    });

    // The members are reached before any strategy is looked at, so both entry points touch them even though no
    // strategy hangs on this AlgoTop and the resolution is therefore empty. Throwing here is the regression.
    assertThat(resolver.resolveForTenant(tenantId)).isEmpty();
    assertThat(resolver.resolveAll()).allMatch(scope -> scope.security() != null);
    assertThat(watchlists.securitiesOfWatchlist(idWatchlist)).hasSize(1);
  }

  private <T> T inTransaction(java.util.function.Supplier<T> work) {
    return new TransactionTemplate(transactionManager).execute(_ -> work.get());
  }
}
