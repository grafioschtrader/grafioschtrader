package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.dto.LimitKey;
import grafiosch.entities.EntityLimit;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.limit.EntityLimitCache;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.*;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.SimulationInitializationMode;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Real resolver and copy writes, with fixture data and limit changes rolled back after each test. */
@GTIntegrationTestContext
@Transactional
@DisplayName("Simulation copies respect all seven target budgets before writing")
class SimulationCopyLimitIntegrationTest {
  private static final LocalDate OPENING = LocalDate.of(2020, 6, 15);
  @PersistenceContext
  private EntityManager em;
  @Autowired
  private SimulationTenantService simulations;
  @Autowired
  private SimulationSourceRepository source;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private HoldCashaccountBalanceJpaRepository balances;
  @Autowired
  private EntityLimitCache limitCache;
  @Autowired
  private MessageSource messages;
  private Integer tenantId;
  private Integer topId;
  private final List<Integer> cashIds = new ArrayList<>();

  @BeforeEach
  void fixture() {
    limitCache.evictAll();
    Tenant tenant = new Tenant("Copy limit fixture", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    tenantId = tenant.getId();
    User user = new User(tenantId);
    user.setIdUser(0);
    var authentication = new UsernamePasswordAuthenticationToken("copy limits", "", List.of());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    for (int i = 0; i < 2; i++) {
      Portfolio portfolio = new Portfolio(tenantId, "Copy portfolio " + i, "CHF");
      em.persist(portfolio);
      Securityaccount securities = new Securityaccount("Copy securities " + i, portfolio);
      securities.setIdTenant(tenantId);
      securities.setLowestTransactionCost(0.0f);
      securities.setTradingPlatformPlan(em.createQuery("SELECT p FROM TradingPlatformPlan p", TradingPlatformPlan.class)
          .setMaxResults(1).getSingleResult());
      em.persist(securities);
      Cashaccount cash = new Cashaccount("Copy cash " + i, 0.0, "CHF", portfolio);
      cash.setIdTenant(tenantId);
      em.persist(cash);
      cashIds.add(cash.getId());
      // Liquidation must count a negative opening balance as one withdrawal too.
      Transaction opening = new Transaction(cash, i == 0 ? 100.0 : -50.0,
          i == 0 ? TransactionType.DEPOSIT : TransactionType.WITHDRAWAL, OPENING.atTime(23, 59));
      opening.setIdTenant(tenantId);
      em.persist(opening);
    }
    Cashaccount first = em.find(Cashaccount.class, cashIds.getFirst());
    Cashaccount zero = new Cashaccount("Copy zero cash", 0.0, "CHF", first.getPortfolio());
    zero.setIdTenant(tenantId);
    em.persist(zero);
    cashIds.add(zero.getId());
    Transaction later = new Transaction(first, 25.0, TransactionType.DEPOSIT, OPENING.plusDays(1).atStartOfDay());
    later.setIdTenant(tenantId);
    em.persist(later);
    List<Currencypair> members = em
        .createQuery("SELECT c FROM Currencypair c ORDER BY c.idSecuritycurrency", Currencypair.class).setMaxResults(2)
        .getResultList();
    assertThat(members).hasSize(2);
    Watchlist selected = new Watchlist(tenantId, "Copy selected universe");
    selected.setSecuritycurrencyList(new ArrayList<>(members));
    em.persist(selected);
    Watchlist unrelated = new Watchlist(tenantId, "Copy unrelated universe");
    unrelated.setSecuritycurrencyList(new ArrayList<>(members));
    em.persist(unrelated);
    AlgoTop top = new AlgoTop();
    top.setIdTenant(tenantId);
    top.setName("Copy limit allocation");
    top.setPercentage(100f);
    top.setIdWatchlist(selected.getId());
    em.persist(top);
    topId = top.getId();
    AlgoAssetclass bucket = new AlgoAssetclass(tenantId, topId, null, 100f);
    bucket.setName("Cash allocation");
    em.persist(bucket);
    em.flush();
    em.clear();
    balances.createCashaccountBalanceEntireByTenant(tenantId);
  }

  @AfterEach
  void clearContext() {
    limitCache.evictAll();
    SecurityContextHolder.clearContext();
  }

  static Stream<Arguments> limits() {
    return Stream.of(Arguments.of(LimitKeyConfig.KEY_PORTFOLIO, 2, "portfolios"),
        Arguments.of(LimitKeyConfig.KEY_SECURITY_ACCOUNT, 2, "securityaccounts"),
        Arguments.of(LimitKeyConfig.KEY_CASH_ACCOUNT, 3, "cashaccounts"),
        Arguments.of(LimitKeyConfig.KEY_WATCHLIST, 1, "watchlists"),
        Arguments.of(LimitKeyConfig.KEY_WATCHLIST_LENGTH, 2, "watchlist.length"),
        Arguments.of(LimitKeyConfig.KEY_SECURITIES_CURRENCIES, 2, "watchlist.total"),
        Arguments.of(LimitKeyConfig.KEY_TRANSACTION, 2, "transactions"));
  }

  @ParameterizedTest(name = "{2}: copy exactly at {1}")
  @MethodSource("limits")
  void copyAtLimitSucceeds(LimitKey key, int rows, String area) throws Exception {
    setLimit(key, rows);
    Tenant simulation = simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO));
    assertCopy(simulation, area, rows);
  }

  @ParameterizedTest(name = "{2}: reject {1} rows above the limit")
  @MethodSource("limits")
  void copyAboveLimitWritesNothing(LimitKey key, int rows, String area) {
    setLimit(key, rows - 1);
    assertRejectedWithoutWrites(request(SimulationInitializationMode.COPY_PORTFOLIO), area, rows, rows - 1);
  }

  @ParameterizedTest
  @EnumSource(value = SimulationInitializationMode.class, names = { "MANUAL_CASH", "LIQUIDATE_TO_CASH" })
  void cashModesCountOnlyNonzeroOpeningBalancesAtLimit(SimulationInitializationMode mode) throws Exception {
    setLimit(LimitKeyConfig.KEY_TRANSACTION, 2);
    Tenant simulation = simulations.createSimulationTenant(request(mode));
    var opening = source.transactions(simulation.getId(), OPENING.plusDays(1));
    assertThat(opening).hasSize(2).allMatch(Transaction::isSimulationOpening);
    assertThat(opening).extracting(Transaction::getCashaccountAmount).containsExactlyInAnyOrder(100.0,
        mode == SimulationInitializationMode.MANUAL_CASH ? 50.0 : -50.0);
  }

  @ParameterizedTest
  @EnumSource(value = SimulationInitializationMode.class, names = { "MANUAL_CASH", "LIQUIDATE_TO_CASH" })
  void cashModesRejectTheFullOpeningAboveLimit(SimulationInitializationMode mode) {
    setLimit(LimitKeyConfig.KEY_TRANSACTION, 1);
    assertRejectedWithoutWrites(request(mode), "transactions", 2, 1);
  }

  @Test
  void missingConfigurationDoesNotLimitAnyCopiedQuantity() throws Exception {
    em.createQuery("DELETE FROM EntityLimit").executeUpdate();
    limitCache.evictAll();
    assertCopy(simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO)), "unlimited",
        0);
  }

  @Test
  void requestingUserOverrideTakesPrecedenceOverDefault() throws Exception {
    setLimit(LimitKeyConfig.KEY_PORTFOLIO, 1);
    User user = new User("copy-limit@example.invalid", "unused", "Copy limit user", "en", 0);
    user.setIdTenant(tenantId);
    user.setLastRoleModifiedTime(LocalDateTime.now());
    em.persist(user);
    ((UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).setDetails(user);
    em.persist(new EntityLimit(LimitKeyConfig.KEY_PORTFOLIO, null, user.getIdUser(), 2, null));
    em.flush();
    limitCache.evictAll();
    assertCopy(simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO)), "user", 2);
  }

  @Test
  void requestingRoleOverrideTakesPrecedenceOverDefault() throws Exception {
    setLimit(LimitKeyConfig.KEY_PORTFOLIO, 1);
    Role role = em.createQuery("SELECT r FROM Role r WHERE r.rolename = ?1", Role.class).setParameter(1, Role.ROLE_USER)
        .getSingleResult();
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    user.setRoleMap(Map.of(Role.ROLE_USER, role));
    user.setMostPrivilegedRole(Role.ROLE_USER);
    em.persist(new EntityLimit(LimitKeyConfig.KEY_PORTFOLIO, role.getIdRole(), null, 2, null));
    em.flush();
    limitCache.evictAll();
    assertCopy(simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO)), "role", 2);
  }

  @Test
  void noWatchlistAndZeroCashUseNoWatchlistOrTransactionBudget() throws Exception {
    em.find(AlgoTop.class, topId).setIdWatchlist(null);
    setLimit(LimitKeyConfig.KEY_WATCHLIST, 0);
    setLimit(LimitKeyConfig.KEY_WATCHLIST_LENGTH, 0);
    setLimit(LimitKeyConfig.KEY_SECURITIES_CURRENCIES, 0);
    setLimit(LimitKeyConfig.KEY_TRANSACTION, 0);
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.MANUAL_CASH);
    dto.setCashBalances(Map.of(cashIds.get(2), 0.0));
    Tenant simulation = simulations.createSimulationTenant(dto);
    assertThat(source.transactions(simulation.getId(), OPENING.plusDays(1))).isEmpty();
    assertThat(watchlists(simulation.getId())).isEmpty();
  }

  @Test
  @DisplayName("Manual cash takes over a zero-balance account with its portfolio and nothing else")
  void manualCashCopiesOnlyTheChosenZeroAccountAndItsPortfolio() throws Exception {
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.MANUAL_CASH);
    dto.setCashBalances(Map.of(cashIds.get(2), 0.0));
    Integer simId = simulations.createSimulationTenant(dto).getId();
    assertThat(source.cashaccounts(simId)).singleElement()
        .satisfies(cash -> assertThat(cash.getName()).isEqualTo("Copy zero cash"));
    assertThat(source.portfolios(simId)).singleElement()
        .satisfies(portfolio -> assertThat(portfolio.getName()).isEqualTo("Copy portfolio 0"));
    assertThat(source.securityaccounts(simId)).singleElement()
        .satisfies(securities -> assertThat(securities.getName()).isEqualTo("Copy securities 0"));
    assertThat(source.transactions(simId, OPENING.plusDays(1))).isEmpty();
  }

  @Test
  @DisplayName("Manual cash counts only the chosen accounts against the copy limits")
  void manualCashLimitsCountOnlyTheChosenAccounts() throws Exception {
    setLimit(LimitKeyConfig.KEY_CASH_ACCOUNT, 1);
    setLimit(LimitKeyConfig.KEY_PORTFOLIO, 1);
    setLimit(LimitKeyConfig.KEY_SECURITY_ACCOUNT, 1);
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.MANUAL_CASH);
    dto.setCashBalances(Map.of(cashIds.get(1), 50.0));
    Integer simId = simulations.createSimulationTenant(dto).getId();
    assertThat(source.portfolios(simId)).singleElement()
        .satisfies(portfolio -> assertThat(portfolio.getName()).isEqualTo("Copy portfolio 1"));
    assertThat(source.securityaccounts(simId)).singleElement()
        .satisfies(securities -> assertThat(securities.getName()).isEqualTo("Copy securities 1"));
    assertThat(source.transactions(simId, OPENING.plusDays(1))).singleElement()
        .satisfies(deposit -> assertThat(deposit.getCashaccountAmount()).isEqualTo(50.0));
  }

  @Test
  @DisplayName("Manual cash without any chosen account is rejected before writing")
  void manualCashWithoutAnyAccountIsRejected() {
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.MANUAL_CASH);
    dto.setCashBalances(Map.of());
    Map<String, Long> before = rowCounts();
    assertThatThrownBy(() -> simulations.createSimulationTenant(dto))
        .isInstanceOfSatisfying(DataViolationException.class, error -> assertThat(error.getDataViolation())
            .singleElement().satisfies(v -> assertThat(v.getMessageKey()).isEqualTo("gt.simulation.manual.cash.empty")));
    assertThat(tenants.countByIdParentTenant(tenantId)).isZero();
    assertThat(rowCounts()).containsExactlyInAnyOrderEntriesOf(before);
  }

  private void setLimit(LimitKey key, int value) {
    List<EntityLimit> rows = em.createQuery("SELECT e FROM EntityLimit e", EntityLimit.class).getResultList();
    rows.stream().filter(row -> row.getLimitKey().equals(key)).forEach(em::remove);
    em.flush();
    em.persist(new EntityLimit(key, null, null, value, null));
    em.flush();
    limitCache.evictAll();
  }

  private void assertRejectedWithoutWrites(SimulationTenantCreateDTO dto, String area, int rows, int limit) {
    Map<String, Long> before = rowCounts();
    assertThatThrownBy(() -> simulations.createSimulationTenant(dto))
        .isInstanceOfSatisfying(DataViolationException.class, error -> {
          assertThat(error.getDataViolation()).singleElement().satisfies(violation -> {
            assertThat(violation.getMessageKey()).isEqualTo("gt.simulation.copy.limit." + area);
            assertThat(violation.getData()).containsExactly((long) rows, limit);
            for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMAN)) {
              assertThat(messages.getMessage(violation.getMessageKey(), violation.getData(), locale))
                  .doesNotContain("gt.simulation", "{0}", "{1}");
            }
          });
        });
    assertThat(tenants.countByIdParentTenant(tenantId)).isZero();
    assertThat(rowCounts()).containsExactlyInAnyOrderEntriesOf(before);
  }

  /** Flush before reading, so these assertions also catch a child accidentally written before rejection. */
  private Map<String, Long> rowCounts() {
    em.flush();
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String table : List.of("tenant", "portfolio", "securitycashaccount", "securityaccount", "cashaccount",
        "securityaccount_trading_period", "watchlist", "watchlist_sec_cur", "transaction", "hold_cashaccount_balance",
        "hold_cashaccount_deposit", "hold_securityaccount_security", "task_data_change")) {
      counts.put(table,
          ((Number) em.createNativeQuery("SELECT COUNT(*) FROM `" + table + "`", Long.class).getSingleResult())
              .longValue());
    }
    return counts;
  }

  private List<Watchlist> watchlists(Integer idTenant) {
    return em.createQuery("SELECT w FROM Watchlist w WHERE w.idTenant = ?1", Watchlist.class).setParameter(1, idTenant)
        .getResultList();
  }

  private void assertCopy(Tenant simulation, String area, int rows) {
    assertThat(source.portfolios(simulation.getId())).as(area + " at " + rows).hasSize(2);
    assertThat(source.securityaccounts(simulation.getId())).hasSize(2);
    assertThat(source.cashaccounts(simulation.getId())).hasSize(3);
    assertThat(watchlists(simulation.getId())).singleElement()
        .satisfies(watchlist -> assertThat(watchlist.getSecuritycurrencyList()).hasSize(2));
    assertThat(source.transactions(simulation.getId(), OPENING.plusDays(2))).hasSize(2)
        .allMatch(Transaction::isSimulationOpening)
        .allMatch(transaction -> transaction.getTransactionDate().equals(OPENING));
    assertThat(source.transactions(tenantId, OPENING.plusDays(2))).hasSize(3);
    assertThat(watchlists(tenantId)).hasSize(2);
  }

  private SimulationTenantCreateDTO request(SimulationInitializationMode mode) {
    SimulationTenantCreateDTO dto = new SimulationTenantCreateDTO();
    dto.setIdAlgoTop(topId);
    dto.setTenantName("Copy limit simulation");
    dto.setSimulationStartDate(OPENING);
    dto.setInitializationMode(mode);
    if (mode == SimulationInitializationMode.MANUAL_CASH)
      dto.setCashBalances(Map.of(cashIds.get(0), 100.0, cashIds.get(1), 50.0, cashIds.get(2), 0.0));
    return dto;
  }
}
