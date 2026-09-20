package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.*;
import grafioschtrader.entities.*;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Isolated fixture rolled back after each test; never starts the numbered resource suites. */
@GTIntegrationTestContext
@Transactional
class SimulationTenantIntegrationTest {
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
  private HoldSecurityaccountSecurityJpaRepository holdings;
  @Autowired
  private AlgoTopJpaRepository tops;
  @Autowired
  private AlgoAssetclassJpaRepository assetclasses;
  @Autowired
  private AlgoSecurityJpaRepository algoSecurities;
  private final LocalDate date = LocalDate.of(2020, 6, 15);
  private Integer tenantId;
  private Integer topId;
  private Integer cashId;

  @BeforeEach
  void fixture() {
    Tenant tenant = new Tenant("Simulation integration", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    tenantId = tenant.getId();
    User user = new User(tenantId);
    user.setIdUser(0);
    var authentication = new UsernamePasswordAuthenticationToken("simulation", "", List.of());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    Portfolio portfolio = new Portfolio(tenantId, "Simulation portfolio", "CHF");
    em.persist(portfolio);
    Cashaccount cash = new Cashaccount("Simulation CHF", 0.0, "CHF", portfolio);
    cash.setIdTenant(tenantId);
    em.persist(cash);
    cashId = cash.getId();
    Transaction opening = new Transaction(cash, 100000.0, TransactionType.DEPOSIT, date.atTime(23, 59));
    opening.setIdTenant(tenantId);
    em.persist(opening);
    Transaction later = new Transaction(cash, 500.0, TransactionType.DEPOSIT, date.plusDays(1).atStartOfDay());
    later.setIdTenant(tenantId);
    em.persist(later);
    Watchlist watchlist = new Watchlist(tenantId, "Simulation universe");
    watchlist.setSecuritycurrencyList(new ArrayList<>());
    em.persist(watchlist);
    AlgoTop top = new AlgoTop();
    top.setIdTenant(tenantId);
    top.setName("Simulation shared");
    top.setPercentage(100f);
    top.setIdWatchlist(watchlist.getId());
    em.persist(top);
    topId = top.getId();
    em.flush();
    em.clear();
    balances.createCashaccountBalanceEntireByTenant(tenantId);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allModesHaveDatedConsistentOpeningLedgersAndIndependentMetadata() throws Exception {
    for (SimulationInitializationMode mode : SimulationInitializationMode.values()) {
      SimulationTenantCreateDTO dto = request(mode);
      if (mode == SimulationInitializationMode.MANUAL_CASH)
        dto.setCashBalances(Map.of(cashId, 12345.67));
      Tenant simulation = simulations.createSimulationTenant(dto);
      assertThat(simulation.getSimulationStartDate()).isEqualTo(date);
      assertThat(simulation.getSimulationInitializationMode()).isEqualTo(mode);
      var copied = source.transactions(simulation.getId(), date.plusDays(2));
      assertThat(copied).hasSize(1).allMatch(Transaction::isSimulationOpening);
      assertThat(copied.getFirst().getTransactionDate()).isEqualTo(date);
      assertThat(copied.getFirst().getCashaccount().getId()).isNotEqualTo(cashId);
      assertThat(copied.getFirst().getCashaccountAmount())
          .isEqualTo(mode == SimulationInitializationMode.MANUAL_CASH ? 12345.67 : 100000.0);
      assertThat(balances.findCashBalancesAtDate(simulation.getId(), date)).hasSize(1);
      assertThat(holdings.findOpenPositionsAtDate(simulation.getId(), date)).isEmpty();
      assertThat(tops.findById(topId)).isPresent();
      simulations.deleteSimulationTenant(simulation.getId());
      assertThat(tenants.findById(simulation.getId())).isEmpty();
      assertThat(tops.findById(topId)).isPresent();
    }
    assertThat(source.transactions(tenantId, date.plusDays(2))).hasSize(2);
  }

  @Test
  void genericTenantUpdateCannotChangeOpeningDate() throws Exception {
    Tenant simulation = simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO));
    Tenant changed = new Tenant();
    org.springframework.beans.BeanUtils.copyProperties(simulation, changed);
    changed.setSimulationStartDate(date.minusDays(1));
    assertThatThrownBy(() -> tenants.saveOnlyAttributes(changed, simulation, Set.of()))
        .isInstanceOf(grafiosch.exceptions.DataViolationException.class);
  }

  @ParameterizedTest
  @EnumSource(value = SimulationInitializationMode.class, names = { "COPY_PORTFOLIO", "LIQUIDATE_TO_CASH" })
  void portfolioStrategyOpensOnTheDayAfterItsReferenceDate(SimulationInitializationMode mode) throws Exception {
    setPortfolioReferenceDate(date.minusDays(1));
    SimulationTenantCreateDTO dto = request(mode);
    assertThat(simulations.previewSimulation(dto).errors).isEmpty();
    Tenant simulation = simulations.createSimulationTenant(dto);
    assertThat(simulation.getSimulationStartDate()).isEqualTo(date);
    assertThat(simulation.getSimulationInitializationMode()).isEqualTo(mode);
    assertThat(balances.findCashBalancesAtDate(simulation.getId(), date).getFirst().getBalance()).isEqualTo(100000);
  }

  @ParameterizedTest
  @ValueSource(strings = { "2020-06-30", "2020-12-31", "2024-02-28", "2024-02-29" })
  void portfolioStartDateUsesCalendarDaysAcrossMonthAndYearBoundaries(String referenceDate) throws Exception {
    LocalDate reference = LocalDate.parse(referenceDate);
    setPortfolioReferenceDate(reference);
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.COPY_PORTFOLIO);
    dto.setSimulationStartDate(reference.plusDays(1));
    assertThat(simulations.previewSimulation(dto).errors).isEmpty();
    assertThat(simulations.createSimulationTenant(dto).getSimulationStartDate()).isEqualTo(reference.plusDays(1));
  }

  @ParameterizedTest
  @ValueSource(ints = { -1, 1 })
  void portfolioStrategyRejectsDifferentStartDatesInPreviewAndCreation(int offset) {
    setPortfolioReferenceDate(date.minusDays(1));
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.COPY_PORTFOLIO);
    dto.setSimulationStartDate(date.plusDays(offset));
    assertPreviewAndCreationRejected(dto, "simulation.portfolio.start.date");
  }

  @Test
  void portfolioStrategyRejectsManualCashInPreviewAndCreation() {
    setPortfolioReferenceDate(date.minusDays(1));
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.MANUAL_CASH);
    dto.setCashBalances(Map.of(cashId, 12345.67));
    assertPreviewAndCreationRejected(dto, "simulation.portfolio.manual.cash");
  }

  @Test
  void portfolioStrategyMustWaitUntilItsFixedStartDateIsCompleted() {
    setPortfolioReferenceDate(LocalDate.now().minusDays(1));
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.COPY_PORTFOLIO);
    dto.setSimulationStartDate(LocalDate.now());
    assertPreviewAndCreationRejected(dto, "algo.completed.date.required");
  }

  /** Marks the fixture as a portfolio-derived strategy without changing its source ledger. */
  private void setPortfolioReferenceDate(LocalDate referenceDate) {
    AlgoTop top = em.find(AlgoTop.class, topId);
    top.setReferenceDate(referenceDate);
    top.setIdWatchlist(null);
    em.flush();
  }

  /** Both entry points must reject the same invalid opening before persisting an environment. */
  private void assertPreviewAndCreationRejected(SimulationTenantCreateDTO dto, String messageKey) {
    assertThatThrownBy(() -> simulations.previewSimulation(dto)).isInstanceOf(DataViolationException.class)
        .satisfies(error -> assertThat(((DataViolationException) error).getDataViolation())
            .extracting(violation -> violation.getMessageKey()).contains(messageKey));
    assertThatThrownBy(() -> simulations.createSimulationTenant(dto)).isInstanceOf(DataViolationException.class)
        .satisfies(error -> assertThat(((DataViolationException) error).getDataViolation())
            .extracting(violation -> violation.getMessageKey()).contains(messageKey));
    assertThat(tenants.countByIdParentTenant(tenantId)).isZero();
  }

  @Test
  void invalidManualAccountIsRejectedBeforeAnyEnvironmentIsWritten() {
    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.MANUAL_CASH);
    dto.setCashBalances(Map.of(-1, 100.0));
    assertThatThrownBy(() -> simulations.createSimulationTenant(dto))
        .isInstanceOf(grafiosch.exceptions.DataViolationException.class);
    assertThat(tenants.countByIdParentTenant(tenantId)).isZero();
  }

  @Test
  void liquidationPreservesNegativeCashAsAnOpeningWithdrawal() throws Exception {
    Transaction debit = new Transaction(em.find(Cashaccount.class, cashId), -100250.0, TransactionType.WITHDRAWAL,
        date.atTime(23, 59, 30));
    debit.setIdTenant(tenantId);
    em.persist(debit);
    em.flush();
    balances.createCashaccountBalanceEntireByTenant(tenantId);
    Tenant simulation = simulations.createSimulationTenant(request(SimulationInitializationMode.LIQUIDATE_TO_CASH));
    Transaction opening = source.transactions(simulation.getId(), date.plusDays(1)).getFirst();
    assertThat(opening.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);
    assertThat(opening.getCashaccountAmount()).isEqualTo(-250);
    assertThat(balances.findCashBalancesAtDate(simulation.getId(), date).getFirst().getBalance()).isEqualTo(-250);
  }

  @Test
  void firstDayAllocationAndLiquidationUseTheSameHistoricalEquity() throws Exception {
    addSecurityPosition();
    AlgoTopCreateFromPortfolio allocation = allocationRequest("Simulation allocation", date);
    AlgoTop generated = tops.saveOnlyAttributes(allocation, null, Set.of());
    em.flush();
    em.clear();
    generated = tops.findById(generated.getId()).orElseThrow();
    assertThat(generated.getIdWatchlist()).isNull();
    assertThat(generated.getPercentage()).isEqualTo(48.2f);
    assertThat(generated.getReferenceDate()).isEqualTo(date);
    Tenant liquidated = simulations.createSimulationTenant(request(SimulationInitializationMode.LIQUIDATE_TO_CASH));
    assertThat(source.transactions(liquidated.getId(), date.plusDays(1))).hasSize(1);
    assertThat(balances.findCashBalancesAtDate(liquidated.getId(), date).getFirst().getBalance()).isEqualTo(100000);
    assertThat(holdings.findOpenPositionsAtDate(liquidated.getId(), date)).isEmpty();
    SimulationTenantCreateDTO copyRequest = request(SimulationInitializationMode.COPY_PORTFOLIO);
    copyRequest.setIdAlgoTop(generated.getId());
    copyRequest.setSimulationStartDate(date.plusDays(1));
    Tenant copied = simulations.createSimulationTenant(copyRequest);
    assertThat(holdings.findOpenPositionsAtDate(copied.getId(), date.plusDays(1)).getFirst().getHodlings())
        .isEqualTo(482);
    assertThat(balances.findCashBalancesAtDate(copied.getId(), date.plusDays(1)).getFirst().getBalance())
        .isEqualTo(52300);
    assertThat(em.createQuery("SELECT w FROM Watchlist w WHERE w.idTenant = ?1", Watchlist.class)
        .setParameter(1, copied.getId()).getResultList()).isEmpty();
  }

  @Test
  void copyRemapsBothDirectionsOfAnAccountTransfer() throws Exception {
    Cashaccount first = em.find(Cashaccount.class, cashId);
    Cashaccount second = new Cashaccount("Simulation second", 0.0, "CHF", first.getPortfolio());
    second.setIdTenant(tenantId);
    em.persist(second);
    Transaction debit = new Transaction(first, -1000.0, TransactionType.WITHDRAWAL, date.atTime(15, 0));
    Transaction credit = new Transaction(second, 1000.0, TransactionType.DEPOSIT, date.atTime(15, 0));
    debit.setIdTenant(tenantId);
    credit.setIdTenant(tenantId);
    em.persist(debit);
    em.persist(credit);
    debit.setConnectedIdTransaction(credit.getId());
    credit.setConnectedIdTransaction(debit.getId());
    em.flush();
    Tenant simulation = simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO));
    var copied = source.transactions(simulation.getId(), date.plusDays(1));
    Set<Integer> copiedIds = copied.stream().map(Transaction::getId).collect(java.util.stream.Collectors.toSet());
    assertThat(copied).hasSize(3);
    assertThat(copied.stream().filter(t -> t.getConnectedIdTransaction() != null)).hasSize(2)
        .allMatch(t -> copiedIds.contains(t.getConnectedIdTransaction()));
    assertThat(copiedIds).doesNotContain(debit.getId(), credit.getId());
    simulations.deleteSimulationTenant(simulation.getId());
    assertThat(source.transactions(tenantId, date.plusDays(2))).hasSize(4);
  }

  private Security addSecurityPosition() {
    return addSecurityPosition(482.0);
  }

  /**
   * Buys one CHF instrument at 100 on the fixture date, puts it on the linked watchlist and rebuilds the hold tables.
   *
   * @param units how many are bought, which decides whether the portfolio stays within its equity
   * @return the instrument that was bought
   */
  private Security addSecurityPosition(double units) {
    Security security = em
        .createQuery("SELECT s FROM Security s WHERE s.name = :name AND s.currency = 'CHF'", Security.class)
        .setParameter("name", "Swisscom AG").getResultList().getFirst();
    priceAt(security, 100.0);
    addTrade(addSecurityaccount(), security, em.find(Cashaccount.class, cashId), units, 100.0, date.atTime(16, 0));
    linkToWatchlist(security);
    em.flush();
    em.clear();
    holdings.createSecurityHoldingsEntireByTenant(tenantId);
    balances.createCashaccountBalanceEntireByTenant(tenantId);
    return em.find(Security.class, security.getId());
  }

  private Securityaccount addSecurityaccount() {
    return addSecurityaccount("Simulation custody");
  }

  private Securityaccount addSecurityaccount(String name) {
    Securityaccount account = new Securityaccount(name, em.find(Cashaccount.class, cashId).getPortfolio());
    account.setIdTenant(tenantId);
    account.setLowestTransactionCost(0f);
    account.setTradingPlatformPlan(em.createQuery("SELECT p FROM TradingPlatformPlan p", TradingPlatformPlan.class)
        .setMaxResults(1).getSingleResult());
    em.persist(account);
    return account;
  }

  /** Books through the first security account of the fixture, which every additional trade of a test reuses. */
  private void addTrade(Security security, Cashaccount cash, double units, double price, LocalDateTime when) {
    addTrade(em.createQuery("SELECT a FROM Securityaccount a WHERE a.idTenant = ?1", Securityaccount.class)
        .setParameter(1, tenantId).setMaxResults(1).getSingleResult(), security, cash, units, price, when);
  }

  /**
   * Persists one trade directly, bypassing the transaction repository so that a fixture may exceed a cash balance or
   * book in whatever order a test needs. The hold tables are rebuilt by the caller afterwards.
   *
   * @param account  the security account the position is held in
   * @param security the instrument
   * @param cash     the cash account the trade settles through
   * @param units    positive to buy, negative to sell
   * @param price    price per unit in the instrument's currency
   * @param when     booking time used to reconstruct the dated holdings
   */
  private void addTrade(Securityaccount account, Security security, Cashaccount cash, double units, double price,
      LocalDateTime when) {
    Transaction trade = new Transaction(account.getId(), cash, security, -units * price, Math.abs(units), price,
        units > 0 ? TransactionType.ACCUMULATE : TransactionType.REDUCE, 0.0, 0.0, null, when, null, null, null, false);
    trade.setIdTenant(tenantId);
    em.persist(trade);
  }

  @Test
  @DisplayName("A legacy watchlist neither restricts nor expands portfolio-derived holdings")
  void legacyWatchlistDoesNotRestrictOrExpandTheAllocation() throws Exception {
    Security held = addSecurityPosition();
    AlgoTop original = tops.findById(topId).orElseThrow();
    Security unheld = chfSecurities(2, null).stream().filter(s -> !s.getId().equals(held.getId())).findFirst()
        .orElseThrow();
    linkToWatchlist(unheld);
    em.flush();
    AlgoTopCreateFromPortfolio allocation = allocationRequest("Simulation legacy watchlist", date);
    allocation.setIdWatchlist(original.getIdWatchlist());
    AlgoTop generated = tops.saveOnlyAttributes(allocation, null, Set.of());
    em.flush();
    em.clear();
    assertThat(tops.findById(generated.getId()).orElseThrow().getIdWatchlist()).isNull();
    assertThat(allocationSecurities(generated)).extracting(s -> s.getSecurity().getId()).containsExactly(held.getId());
  }

  @Test
  @DisplayName("Portfolio strategy aggregates only positions held at the reference date")
  void allocationUsesOnlyHoldingsAtTheReferenceDateAcrossAccounts() throws Exception {
    List<Security> securities = chfSecurities(3, null);
    Security held = securities.get(0);
    Security soldBefore = securities.get(1);
    Security boughtAfter = securities.get(2);
    Securityaccount first = addSecurityaccount();
    Securityaccount second = addSecurityaccount("Simulation other custody");
    Cashaccount cash = em.find(Cashaccount.class, cashId);
    addTrade(first, held, cash, 100, 100, date.minusDays(2).atTime(16, 0));
    addTrade(second, held, cash, 25, 100, date.atTime(16, 0));
    addTrade(first, held, cash, -100, 100, date.plusDays(1).atTime(16, 0));
    addTrade(second, held, cash, -25, 100, date.plusDays(1).atTime(16, 5));
    addTrade(first, soldBefore, cash, 50, 100, date.minusDays(2).atTime(16, 5));
    addTrade(first, soldBefore, cash, -50, 100, date.minusDays(1).atTime(16, 0));
    addTrade(first, boughtAfter, cash, 25, 100, date.plusDays(1).atTime(16, 10));
    em.flush();
    em.clear();
    holdings.createSecurityHoldingsEntireByTenant(tenantId);
    balances.createCashaccountBalanceEntireByTenant(tenantId);

    AlgoTop generated = tops.saveOnlyAttributes(allocationRequest("Simulation historical holdings", date), null,
        Set.of());
    assertThat(generated.getPercentage()).isEqualTo(12.5f);
    assertThat(generated.getReferenceDate()).isEqualTo(date);
    assertThat(generated.getIdWatchlist()).isNull();
    List<AlgoSecurity> members = allocationSecurities(generated);
    assertThat(members).extracting(s -> s.getSecurity().getId()).containsExactly(held.getId());
    assertThat(members.getFirst().getPercentage()).isEqualTo(100f);
  }

  @Test
  void sharedStrategyKeepsIndependentOpeningDatesAndEnforcesEnvironmentLimit() throws Exception {
    SimulationTenantCreateDTO first = request(SimulationInitializationMode.COPY_PORTFOLIO);
    Tenant firstEnvironment = simulations.createSimulationTenant(first);
    SimulationTenantCreateDTO second = request(SimulationInitializationMode.COPY_PORTFOLIO);
    second.setSimulationStartDate(date.plusDays(1));
    Tenant secondEnvironment = simulations.createSimulationTenant(second);
    assertThat(firstEnvironment.getSimulationStartDate()).isEqualTo(date);
    assertThat(secondEnvironment.getSimulationStartDate()).isEqualTo(date.plusDays(1));
    assertThat(balances.findCashBalancesAtDate(secondEnvironment.getId(), date.plusDays(1)).getFirst().getBalance())
        .isEqualTo(100500);
    assertThat(tops.findById(topId).orElseThrow().getReferenceDate()).isNull();
    for (int i = 2; i < 5; i++)
      simulations.createSimulationTenant(first);
    assertThatThrownBy(() -> simulations.createSimulationTenant(first))
        .isInstanceOf(grafiosch.exceptions.DataViolationException.class);
    assertThat(tenants.countByIdParentTenant(tenantId)).isEqualTo(5);
  }

  @Test
  void openingDateBeforeTheFirstSourceTransactionIsRejectedUnlessNothingIsRead() throws Exception {
    for (SimulationInitializationMode mode : SimulationInitializationMode.values()) {
      SimulationTenantCreateDTO dto = request(mode);
      dto.setSimulationStartDate(date.minusDays(1));
      if (mode == SimulationInitializationMode.MANUAL_CASH) {
        // Manual cash copies nothing from the source, so a day before its first transaction is a legitimate origin.
        dto.setCashBalances(Map.of(cashId, 7000.0));
        Tenant manual = simulations.createSimulationTenant(dto);
        assertThat(manual.getSimulationStartDate()).isEqualTo(date.minusDays(1));
        assertThat(balances.findCashBalancesAtDate(manual.getId(), date.minusDays(1)).getFirst().getBalance())
            .isEqualTo(7000);
      } else {
        assertThatThrownBy(() -> simulations.createSimulationTenant(dto))
            .isInstanceOf(grafiosch.exceptions.DataViolationException.class);
      }
    }
    assertThat(tenants.countByIdParentTenant(tenantId)).isEqualTo(1);
  }

  @Test
  void ambiguousPositionAttributionBlocksCreationUntilADestinationIsChosen() throws Exception {
    Security security = addSecurityPosition();
    Cashaccount second = addCashaccount("Simulation second");
    // Bought through two accounts and partly sold through the smaller one, so its attributed quantity ends up
    // negative while the position is long. Nothing says which account the remaining 464 units belong to.
    addTrade(security, second, 100.0, 100.0, date.atTime(16, 30));
    addTrade(security, second, -118.0, 100.0, date.atTime(16, 45));
    em.flush();
    em.clear();
    holdings.createSecurityHoldingsEntireByTenant(tenantId);
    balances.createCashaccountBalanceEntireByTenant(tenantId);

    SimulationTenantCreateDTO dto = request(SimulationInitializationMode.LIQUIDATE_TO_CASH);
    SimulationPreviewDto preview = simulations.previewSimulation(dto);
    assertThat(preview.errors).isEmpty();
    assertThat(preview.unresolvedPositions).hasSize(1);
    assertThat(preview.unresolvedPositions.getFirst().units()).isEqualTo(464);
    assertThatThrownBy(() -> simulations.createSimulationTenant(dto))
        .isInstanceOf(grafiosch.exceptions.DataViolationException.class);

    dto.setLiquidationAssignments(Map.of(preview.unresolvedPositions.getFirst().positionKey(), cashId));
    Tenant resolved = simulations.createSimulationTenant(dto);
    assertThat(holdings.findOpenPositionsAtDate(resolved.getId(), date)).isEmpty();
    // 50000 left on the first account plus the 48200 of proceeds routed to it, and 1800 on the second.
    assertThat(balances.findCashBalancesAtDate(resolved.getId(), date).stream().map(HoldCashaccountBalance::getBalance)
        .toList()).containsExactlyInAnyOrder(98200.0, 1800.0);
  }

  @Test
  void onlyTheWatchlistOfTheLinkedStrategyIsCopied() throws Exception {
    addSecurityPosition();
    Watchlist unrelated = new Watchlist(tenantId, "Simulation unrelated");
    unrelated.setSecuritycurrencyList(new ArrayList<>());
    em.persist(unrelated);
    em.flush();
    Tenant simulation = simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO));
    List<Watchlist> copied = em.createQuery("SELECT w FROM Watchlist w WHERE w.idTenant = ?1", Watchlist.class)
        .setParameter(1, simulation.getId()).getResultList();
    assertThat(copied).hasSize(1);
    assertThat(copied.getFirst().getName()).isEqualTo("Simulation universe");
    assertThat(copied.getFirst().getSecuritycurrencyList()).hasSize(1);
  }

  @Test
  void anEnvironmentWithoutAnOpeningDefinitionAsksToBeRecreated() throws Exception {
    Tenant simulation = simulations.createSimulationTenant(request(SimulationInitializationMode.COPY_PORTFOLIO));
    em.createQuery("""
        UPDATE Tenant t SET t.simulationStartDate = NULL, t.simulationInitializationMode = NULL
          WHERE t.idTenant = ?1""").setParameter(1, simulation.getId()).executeUpdate();
    em.clear();
    List<SimulationTenantInfo> environments = simulations.getSimulationTenants();
    assertThat(environments).hasSize(1);
    assertThat(environments.getFirst().isRequiresRecreation()).isTrue();
  }

  @Test
  void aReferenceDateBeforeTheFirstTransactionIsRejected() {
    addSecurityPosition();
    long before = tops.count();
    AlgoTopCreateFromPortfolio allocation = allocationRequest("Simulation too early", date.minusDays(1));
    assertThatThrownBy(() -> tops.saveOnlyAttributes(allocation, null, Set.of()))
        .isInstanceOf(grafiosch.exceptions.DataViolationException.class);
    assertThat(tops.count()).isEqualTo(before);
  }

  @Test
  void aLeveragedPortfolioCannotBeExpressedAsAnAllocationAndTheErrorNamesTheFigures() {
    // 148200 of exposure against 100000 of equity: the cash account goes negative, so gross exceeds equity.
    addSecurityPosition(1482.0);
    AlgoTopCreateFromPortfolio allocation = allocationRequest("Simulation leveraged", date);
    assertThatThrownBy(() -> tops.saveOnlyAttributes(allocation, null, Set.of()))
        .isInstanceOfSatisfying(grafiosch.exceptions.DataViolationException.class, violation -> {
          var reported = violation.getDataViolation().getFirst();
          assertThat(reported.getMessageKey()).isEqualTo("algo.allocation.invalid");
          assertThat(reported.getData()[0]).asString().contains("100000.00").contains("148200.00");
        });
  }

  @Test
  void weightsAreNormalizedWithinEachBucketAndAcrossBuckets() throws Exception {
    List<Security> shares = chfSecurities(2, null);
    Security bond = chfSecurities(1, shares.getFirst().getAssetClass().getId()).getFirst();
    Securityaccount account = addSecurityaccount();
    // 30000 + 10000 of one asset class and 10000 of another, against 50000 of remaining cash.
    addTrade(account, shares.get(0), em.find(Cashaccount.class, cashId), 300.0, 100.0, date.atTime(16, 0));
    addTrade(account, shares.get(1), em.find(Cashaccount.class, cashId), 100.0, 100.0, date.atTime(16, 5));
    addTrade(account, bond, em.find(Cashaccount.class, cashId), 100.0, 100.0, date.atTime(16, 10));
    em.flush();
    em.clear();
    holdings.createSecurityHoldingsEntireByTenant(tenantId);
    balances.createCashaccountBalanceEntireByTenant(tenantId);

    AlgoTop generated = tops.saveOnlyAttributes(allocationRequest("Simulation two buckets", date), null, Set.of());
    assertThat(generated.getPercentage()).isEqualTo(50f);
    List<AlgoAssetclass> buckets = assetclasses.findByIdTenantAndIdAlgoAssetclassParent(tenantId, generated.getId());
    assertThat(buckets).hasSize(2);
    assertThat(buckets.stream().map(AlgoAssetclass::getPercentage)).containsExactlyInAnyOrder(80f, 20f);
    List<Float> securityWeights = buckets.stream()
        .flatMap(b -> algoSecurities.findByIdAlgoSecurityParentAndIdTenant(b.getId(), tenantId).stream())
        .map(AlgoSecurity::getPercentage).toList();
    assertThat(securityWeights).containsExactlyInAnyOrder(75f, 25f, 100f);
  }

  private AlgoTopCreateFromPortfolio allocationRequest(String name, LocalDate referenceDate) {
    AlgoTopCreateFromPortfolio allocation = new AlgoTopCreateFromPortfolio();
    allocation.setIdTenant(tenantId);
    allocation.setName(name);
    allocation.setReferenceDate(referenceDate);
    return allocation;
  }

  private List<AlgoSecurity> allocationSecurities(AlgoTop top) {
    return assetclasses.findByIdTenantAndIdAlgoAssetclassParent(tenantId, top.getId()).stream()
        .flatMap(bucket -> algoSecurities.findByIdAlgoSecurityParentAndIdTenant(bucket.getId(), tenantId).stream())
        .toList();
  }

  /**
   * Picks CHF securities that are neither derived nor margin instruments and gives each of them a closing price on the
   * fixture date, so the historical valuation has everything it needs.
   *
   * @param count           how many are needed
   * @param excludeIdAssetC asset class to skip, or null to take whichever comes first
   * @return the securities, in a stable order
   */
  private List<Security> chfSecurities(int count, Integer excludeIdAssetC) {
    List<Security> securities = em.createQuery("""
        SELECT s FROM Security s WHERE s.currency = 'CHF' AND s.idLinkSecuritycurrency IS NULL
          AND s.assetClass IS NOT NULL AND (?1 IS NULL OR s.assetClass.idAssetClass <> ?1)
          ORDER BY s.assetClass.idAssetClass, s.idSecuritycurrency""", Security.class).setParameter(1, excludeIdAssetC)
        .setMaxResults(count).getResultList();
    assertThat(securities).as("CHF securities in the test database").hasSize(count);
    securities.forEach(security -> priceAt(security, 100.0));
    return securities;
  }

  private void priceAt(Security security, double close) {
    List<Historyquote> quotes = em
        .createQuery("SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.date = ?2", Historyquote.class)
        .setParameter(1, security.getId()).setParameter(2, date).getResultList();
    if (quotes.isEmpty())
      em.persist(new Historyquote(security.getId(), HistoryquoteCreateType.CONNECTOR_CREATED, date, close));
    else
      quotes.getFirst().setClose(close);
  }

  private void linkToWatchlist(Security... securities) {
    em.find(Watchlist.class, tops.findById(topId).orElseThrow().getIdWatchlist())
        .setSecuritycurrencyList(new ArrayList<>(List.of(securities)));
  }

  private Cashaccount addCashaccount(String name) {
    Cashaccount account = new Cashaccount(name, 0.0, "CHF", em.find(Cashaccount.class, cashId).getPortfolio());
    account.setIdTenant(tenantId);
    em.persist(account);
    return account;
  }

  private SimulationTenantCreateDTO request(SimulationInitializationMode mode) {
    SimulationTenantCreateDTO dto = new SimulationTenantCreateDTO();
    dto.setIdAlgoTop(topId);
    dto.setTenantName("Sim " + mode);
    dto.setSimulationStartDate(date);
    dto.setInitializationMode(mode);
    return dto;
  }
}
