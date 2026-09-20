package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The strategy hierarchy is read only while a request operates in a simulation environment.
 *
 * <p>
 * Every mutation is issued against the repository implementations rather than through REST, because that is where the
 * guard lives and because {@code AlgoBaseResource} re-points algo writes at the home tenant - a resource-level test
 * would prove the refusal for the REST path only, while an import, a bulk operation or an internal caller slipped past.
 * Each refusal is paired with an assertion that the home rows are unchanged, so that a guard which threw only after
 * writing would still fail.
 * </p>
 *
 * <p>
 * Isolated fixture rolled back after each test; never starts the numbered resource suites.
 * </p>
 */
@GTIntegrationTestContext
@Transactional
class AlgoHierarchySimulationGuardTest {

  private static final String MESSAGE_KEY = "gt.simulation.algo.read.only";

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;
  @Autowired
  private AlgoAssetclassJpaRepository algoAssetclassJpaRepository;
  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;
  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  private User user;
  private Integer homeIdTenant;
  private Integer simulationIdTenant;
  private Integer unrelatedIdTenant;
  private AlgoTop top;
  private AlgoAssetclass bucket;
  private AlgoSecurity member;
  private AlgoStrategy strategy;
  private Integer idSecuritycurrency;

  @BeforeEach
  void fixture() {
    Tenant home = new Tenant("Guard home", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(home);
    homeIdTenant = home.getId();

    Tenant simulation = new Tenant("Guard simulation", "CHF", 0, TenantKindType.SIMULATION_COPY, false);
    simulation.setIdParentTenant(homeIdTenant);
    em.persist(simulation);
    simulationIdTenant = simulation.getId();

    Tenant unrelated = new Tenant("Guard unrelated", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(unrelated);
    unrelatedIdTenant = unrelated.getId();

    Watchlist watchlist = new Watchlist(homeIdTenant, "Guard universe");
    em.persist(watchlist);

    top = new AlgoTop();
    top.setIdTenant(homeIdTenant);
    top.setName("Guard strategy");
    top.setPercentage(100f);
    top.setIdWatchlist(watchlist.getId());
    em.persist(top);

    Security security = em.createQuery("SELECT s FROM Security s ORDER BY s.idSecuritycurrency", Security.class)
        .setMaxResults(1).getSingleResult();
    idSecuritycurrency = security.getIdSecuritycurrency();

    bucket = new AlgoAssetclass();
    bucket.setIdTenant(homeIdTenant);
    bucket.setIdAlgoAssetclassParent(top.getId());
    bucket.setName("Guard bucket");
    bucket.setPercentage(60f);
    em.persist(bucket);

    member = new AlgoSecurity();
    member.setIdTenant(homeIdTenant);
    member.setIdAlgoSecurityParent(bucket.getId());
    member.setSecurity(security);
    member.setPercentage(40f);
    em.persist(member);

    strategy = new AlgoStrategy();
    strategy.setIdTenant(homeIdTenant);
    strategy.setIdAlgoAssetclassSecurity(top.getId());
    strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE);
    strategy.setStrategyConfig("{\"upperValue\":100}");
    em.persist(strategy);

    em.flush();
    authenticate(homeIdTenant);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  /** Puts the request into the given tenant while the user's own tenant stays the home one. */
  private void authenticate(Integer currentIdTenant) {
    user = new User(homeIdTenant);
    user.setIdUser(0);
    user.setIdTenant(currentIdTenant);
    user.setActualIdTenant(homeIdTenant);
    var authentication = new UsernamePasswordAuthenticationToken("guard", "", List.of());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private Set<Class<? extends Annotation>> levels() {
    return Set.of(PropertyAlwaysUpdatable.class);
  }

  /** The persisted hierarchy of the home tenant, as the assertions compare it before and after a refusal. */
  private String homeState() {
    em.flush();
    em.clear();
    return algoTopJpaRepository.findByIdTenantOrderByName(homeIdTenant).stream()
        .map(t -> t.getName() + "/" + t.getPercentage() + "/" + t.isActivatable()).toList().toString()
        + algoAssetclassJpaRepository.findByIdTenantAndIdAlgoAssetclassParent(homeIdTenant, top.getId()).stream()
            .map(a -> a.getName() + "/" + a.getPercentage()).toList().toString()
        + algoSecurityJpaRepository.findByIdAlgoSecurityParentAndIdTenant(bucket.getId(), homeIdTenant).stream()
            .map(m -> String.valueOf(m.getPercentage())).toList().toString()
        + algoStrategyJpaRepository.findByIdAlgoAssetclassSecurityAndIdTenant(top.getId(), homeIdTenant).size();
  }

  @Test
  @DisplayName("Every hierarchy mutation is refused in a simulation and leaves the home rows untouched")
  void allMutationsRefusedInSimulation() {
    String before = homeState();
    authenticate(simulationIdTenant);

    AlgoTop topChange = algoTopJpaRepository.findById(top.getId()).orElseThrow();
    topChange.setActivatable(!topChange.isActivatable());
    assertRefused(() -> algoTopJpaRepository.saveOnlyAttributes(topChange, topChange, levels()));
    assertRefused(() -> algoTopJpaRepository.normalizeChildPercentages(top.getId(), homeIdTenant));
    assertRefused(() -> algoTopJpaRepository.normalizeAllPercentages(top.getId(), homeIdTenant));
    assertRefused(() -> algoTopJpaRepository.delEntityWithTenant(top.getId(), homeIdTenant));

    AlgoAssetclass bucketChange = algoAssetclassJpaRepository.findById(bucket.getId()).orElseThrow();
    bucketChange.setPercentage(1f);
    assertRefused(() -> algoAssetclassJpaRepository.saveOnlyAttributes(bucketChange, bucketChange, levels()));
    assertRefused(() -> algoAssetclassJpaRepository.delEntityWithTenant(bucket.getId(), homeIdTenant));

    AlgoSecurity memberChange = algoSecurityJpaRepository.findById(member.getId()).orElseThrow();
    memberChange.setPercentage(2f);
    assertRefused(() -> algoSecurityJpaRepository.saveOnlyAttributes(memberChange, memberChange, levels()));
    assertRefused(() -> algoSecurityJpaRepository.delEntityWithTenant(member.getId(), homeIdTenant));

    AlgoStrategy strategyChange = algoStrategyJpaRepository.findById(strategy.getId()).orElseThrow();
    assertRefused(() -> algoStrategyJpaRepository.saveOnlyAttributes(strategyChange, strategyChange, levels()));
    assertRefused(() -> algoStrategyJpaRepository.delEntityWithTenant(strategy.getId(), homeIdTenant));

    assertThat(homeState()).as("home hierarchy after every refusal").isEqualTo(before);
  }

  @Test
  @DisplayName("The same mutations succeed from the home tenant")
  void mutationsAllowedFromHome() {
    AlgoTop topChange = algoTopJpaRepository.findById(top.getId()).orElseThrow();
    topChange.setActivatable(false);
    assertThatCode(() -> algoTopJpaRepository.saveOnlyAttributes(topChange, topChange, levels()))
        .doesNotThrowAnyException();
    assertThat(algoTopJpaRepository.findById(top.getId()).orElseThrow().isActivatable()).isFalse();

    algoTopJpaRepository.normalizeChildPercentages(top.getId(), homeIdTenant);
    em.flush();
    em.clear();
    assertThat(algoAssetclassJpaRepository.findByIdTenantAndIdAlgoAssetclassParent(homeIdTenant, top.getId()))
        .singleElement().extracting(AlgoAssetclass::getPercentage).isEqualTo(100f);

    assertThat(algoStrategyJpaRepository.delEntityWithTenant(strategy.getId(), homeIdTenant)).isEqualTo(1);
  }

  @Test
  @DisplayName("An unrelated current tenant is not this guard's business")
  void unrelatedTenantDoesNotTripTheGuard() {
    // A token naming a foreign tenant is rejected by the tenant context filter long before a repository is reached;
    // answering here as well would produce a second, misleading message.
    authenticate(unrelatedIdTenant);
    AlgoTop topChange = algoTopJpaRepository.findById(top.getId()).orElseThrow();
    assertThatCode(() -> algoTopJpaRepository.saveOnlyAttributes(topChange, topChange, levels()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("The alert setup endpoint neither reads nor creates a node in a simulation")
  void alertNodeLookupRefusedInSimulation() {
    long before = algoSecurityJpaRepository.count();
    authenticate(simulationIdTenant);
    assertRefused(
        () -> algoSecurityJpaRepository.getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(idSecuritycurrency));
    em.flush();
    assertThat(algoSecurityJpaRepository.count()).as("no node created").isEqualTo(before);
  }

  @Test
  @DisplayName("A read-only user may look an alert node up but never create one")
  void alertNodeCreationRefusedForReadOnlyUser() {
    long before = algoSecurityJpaRepository.count();
    user.setTenantAccessReadOnly(true);
    assertThatThrownBy(
        () -> algoSecurityJpaRepository.getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(idSecuritycurrency))
            .isInstanceOf(SecurityException.class);
    em.flush();
    assertThat(algoSecurityJpaRepository.count()).as("no node created").isEqualTo(before);
  }

  @Test
  @DisplayName("A writable home user creates the alert node once and reuses it afterwards")
  void alertNodeCreatedOnceFromHome() {
    var created = algoSecurityJpaRepository.getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(idSecuritycurrency);
    assertThat(created.wasCreated).isTrue();
    long afterCreation = algoSecurityJpaRepository.count();

    var reused = algoSecurityJpaRepository.getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(idSecuritycurrency);
    assertThat(reused.wasCreated).isFalse();
    assertThat(algoSecurityJpaRepository.count()).isEqualTo(afterCreation);
  }

  private void assertRefused(ThrowingCallable mutation) {
    assertThatThrownBy(mutation).isInstanceOf(DataViolationException.class)
        .satisfies(e -> assertThat(((DataViolationException) e).getDataViolation()).isNotEmpty()
            .allMatch(violation -> MESSAGE_KEY.equals(violation.getMessageKey())));
  }
}
