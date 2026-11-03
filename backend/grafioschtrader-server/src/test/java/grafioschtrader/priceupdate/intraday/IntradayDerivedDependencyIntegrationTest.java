package grafioschtrader.priceupdate.intraday;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.service.GTNetLastpriceService;
import grafioschtrader.service.GTNetLastpriceService.SecurityCurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Checks against the test database that the intraday price of a derived security is calculated from the current price
 * of the instruments it is derived from. The chain is a currency pair without intraday connector, a derived security on
 * that pair and a second derived security on the first one, so no external provider is involved.
 */
@GTIntegrationTestContext
class IntradayDerivedDependencyIntegrationTest {

  private static final double STALE_PRICE = 1.0;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;
  @Autowired
  private GTNetLastpriceService gtNetLastpriceService;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @PersistenceContext
  private EntityManager em;

  private TransactionTemplate transaction;
  private Integer idCurrencypair;
  private Integer idDerivedOnPair;
  private Integer idDerivedOnDerived;
  private final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

  @BeforeEach
  void createChain() {
    transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(_ -> {
      Currencypair currencypair = new Currencypair("ISK", "HUF");
      currencypair.setSLast(STALE_PRICE);
      currencypair.setSTimestamp(now.minusDays(1));
      em.persist(currencypair);
      idCurrencypair = currencypair.getId();
      idDerivedOnPair = persistDerived("Derived on currency pair", idCurrencypair);
      idDerivedOnDerived = persistDerived("Derived on derived security", idDerivedOnPair);
    });
  }

  private Integer persistDerived(String name, Integer idLink) {
    Assetclass assetclass = em.createQuery("SELECT a FROM Assetclass a", Assetclass.class).getResultStream()
        .filter(a -> a.getCategoryType() == AssetclassType.EQUITIES
            && a.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT)
        .findFirst().orElseThrow();
    Stockexchange exchange = em.createQuery("SELECT s FROM Stockexchange s", Stockexchange.class).setMaxResults(1)
        .getSingleResult();
    Security security = new Security(name, "HUF", assetclass, exchange, LocalDate.of(2020, 1, 1),
        LocalDate.of(2099, 12, 31), DistributionFrequency.DF_NONE, null, null);
    security.setIdLinkSecuritycurrency(idLink);
    security.setLeverageFactor(1.0f);
    security.setSLast(STALE_PRICE);
    security.setSTimestamp(now.minusDays(2));
    em.persist(security);
    return security.getId();
  }

  @AfterEach
  void removeChain() {
    transaction.executeWithoutResult(_ -> {
      em.remove(em.find(Security.class, idDerivedOnDerived));
      em.remove(em.find(Security.class, idDerivedOnPair));
      em.remove(em.find(Currencypair.class, idCurrencypair));
    });
  }

  @Test
  @DisplayName("A derived security on a derived security outside the list gets the current price of the base")
  void chainOutsideTheListIsCalculatedFirst() {
    transaction.executeWithoutResult(_ -> {
      Currencypair currencypair = em.find(Currencypair.class, idCurrencypair);
      currencypair.setSLast(2.0);
      currencypair.setSTimestamp(now);
    });
    Security derivedOnDerived = securityJpaRepository.findById(idDerivedOnDerived).orElseThrow();

    List<Security> result = securityJpaRepository.updateLastPriceByList(List.of(derivedOnDerived));

    assertThat(result).singleElement().satisfies(s -> {
      assertThat(s.getIdSecuritycurrency()).isEqualTo(idDerivedOnDerived);
      assertThat(s.getSLast()).isEqualTo(2.0);
    });
    assertThat(securityJpaRepository.findById(idDerivedOnPair).orElseThrow().getSLast()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("A watchlist currency pair refreshed in memory, as GTNet does, is used instead of the database copy")
  void refreshedCurrencypairOfTheWatchlistIsUsed() {
    Currencypair refreshedPair = transaction.execute(_ -> em.find(Currencypair.class, idCurrencypair));
    // A price received from a GTNet peer is only set on the instance; the database still holds the stale price.
    refreshedPair.setSLast(3.0);
    refreshedPair.setSTimestamp(now);
    Security derivedOnPair = securityJpaRepository.findById(idDerivedOnPair).orElseThrow();

    SecurityCurrency result = gtNetLastpriceService.updateLastpriceIncludeSupplier(List.of(derivedOnPair),
        List.of(refreshedPair), List.of());

    assertThat(result.securities).singleElement().satisfies(s -> assertThat(s.getSLast()).isEqualTo(3.0));
    assertThat(result.currencypairs).singleElement().satisfies(c -> assertThat(c.getSLast()).isEqualTo(3.0));
  }
}
