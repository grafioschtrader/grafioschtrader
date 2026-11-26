package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.validator.routines.checkdigit.ISINCheckDigit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.dto.SpinOffDecisionDto;
import grafioschtrader.dto.SpinOffPreviewDto.Decision;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.instrument.lookup.SuccessorSecurityProposer;
import grafioschtrader.reports.RealizedGainCalculator;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.SecurityActionJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepositoryImpl;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.rest.SecurityActionResource;
import grafioschtrader.task.exec.RebuildHolingAllTenantOrSingleTask;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.SecurityActionRole;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.SpinOffHistoryBasis;
import grafioschtrader.types.SpinOffHistoryBasisOrigin;
import grafioschtrader.types.SpinOffTaxTreatmentDe;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

/** Uses the migrated test schema and records each scenario inside a rollback-only test transaction. */
@GTIntegrationTestContext
@Transactional
class SpinOffRecordingIntegrationTest {
  private static final LocalDate EX_DATE = LocalDate.of(2026, 3, 2);

  @Autowired
  private EntityManager em;
  @Autowired
  private SecurityActionResource resource;
  @Autowired
  private SecurityActionJpaRepository actions;
  @Autowired
  private HistoryquoteJpaRepository quotes;
  @Autowired
  private ObjectMapper mapper;
  @Autowired
  private TransactionJpaRepository transactions;
  @Autowired
  private RebuildHolingAllTenantOrSingleTask rebuild;
  @Autowired
  private HoldCashaccountBalanceJpaRepository balance;
  @Autowired
  private HoldCashaccountDepositJpaRepository deposit;
  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdings;
  @Autowired
  private SecurityCalcService calc;
  @Autowired
  private TaskDataChangeJpaRepository tasks;
  @Autowired
  private SecurityJpaRepository securities;
  @MockitoBean
  private SuccessorSecurityProposer proposer;

  private Security parent;
  private User admin;

  @BeforeEach
  void fixtures() throws Exception {
    admin = new User(1);
    admin.setIdUser(0);
    admin.setLocaleStr("en");
    var authentication = new UsernamePasswordAuthenticationToken("spin-off admin", "",
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    authentication.setDetails(admin);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    parent = equity("Spin-off parent", "CH998765430");
    em.persist(parent);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recordsExistingChildAndCorrectsOnlyTheAllocationFields() throws Exception {
    Security child = equity("Existing spin-off child", "CH998765431");
    em.persist(child);
    SecurityAction saved = resource.createSecurityAction(request(child)).getBody();
    assertThat(saved.getSecurityNew().getId()).isEqualTo(child.getId());

    Integer active = application(saved, false);
    Integer reversed = application(saved, true);
    SecurityAction correction = mapper.readValue(mapper.writeValueAsString(saved), SecurityAction.class);
    correction.setCostAllocation(0.25);
    correction.setCostAllocationSource("Issuer notice");
    resource.correctSpinOff(saved.getId(), correction);
    em.flush();
    em.clear();

    SecurityAction updated = actions.findById(saved.getId()).orElseThrow();
    assertThat(updated.getCostAllocation()).isEqualTo(0.25);
    assertThat(updated.getNote()).startsWith("Original note\n").endsWith(": allocation 0.2 -> 0.25 (Issuer notice)");
    assertThat(em.find(SecurityActionApplication.class, active).getCostAllocation()).isEqualTo(0.25);
    assertThat(em.find(SecurityActionApplication.class, reversed).getCostAllocation()).isEqualTo(0.20);

    correction.setActionDate(EX_DATE.plusDays(1));
    assertViolation(() -> resource.correctSpinOff(saved.getId(), correction), "gt.security.action.immutable");
    assertViolation(() -> resource.applySecurityAction(saved.getId()), "gt.security.action.spinoff.preview.stale");
  }

  @Test
  void createsTheProposedChildAndRetainsItsEarlySplit() throws Exception {
    Security draft = equity("Child from lookup", "CH998765432");
    when(proposer.proposeSpinOffChild(any(), eq(draft.getIsin()), eq("CHF"), eq(EX_DATE), any()))
        .thenReturn(Optional.of(draft));
    SecurityAction saved = resource.createSecurityAction(request(draft)).getBody();
    Security successor = equity("ISIN successor", "CH998765433");
    em.persist(successor);
    SecurityAction isinChange = request(successor);
    isinChange.setActionType(SecurityActionType.ISIN_CHANGE);
    isinChange.setIsinOld(parent.getIsin());
    isinChange.setCreatedBy(admin.getIdUser());
    isinChange.setCreationTime(LocalDateTime.now());
    em.persist(isinChange);
    em.persist(new Historyquote(saved.getSecurityNew().getId(), HistoryquoteCreateType.CONNECTOR_CREATED, EX_DATE, 48));
    em.persist(new Historyquote(successor.getId(), HistoryquoteCreateType.CONNECTOR_CREATED, EX_DATE, 64));
    em.flush();

    assertThat(em.find(Security.class, saved.getSecurityNew().getId()).getName()).isEqualTo("Child from lookup");
    var importer = new SecuritysplitJpaRepositoryImpl();
    ReflectionTestUtils.setField(importer, "securityActionJpaRepository", actions);
    ReflectionTestUtils.setField(importer, "historyquoteJpaRepository", quotes);
    var childSplit = new Securitysplit(saved.getSecurityNew().getId(), EX_DATE.minusDays(1), 1, 2,
        CreateType.CONNECTOR_CREATED);
    var successorSplit = new Securitysplit(successor.getId(), EX_DATE.minusDays(1), 1, 2, CreateType.CONNECTOR_CREATED);
    assertThat(ReflectionTestUtils.<List<Securitysplit>>invokeMethod(importer, "removeSplitsOfPredecessor",
        saved.getSecurityNew(), List.of(childSplit))).containsExactly(childSplit);
    assertThat(ReflectionTestUtils.<List<Securitysplit>>invokeMethod(importer, "removeSplitsOfPredecessor", successor,
        List.of(successorSplit))).isEmpty();
    assertViolation(() -> resource.createSecurityAction(request(saved.getSecurityNew())),
        "gt.security.action.spinoff.duplicate");
  }

  @Test
  void rejectsAnUnresolvedChild() throws Exception {
    Security unknown = equity("Unknown child", "CH998765434");
    assertViolation(() -> resource.createSecurityAction(request(unknown)), "gt.security.action.spinoff.child.missing");
    assertThat(actions.findAllByOrderByActionDateDesc())
        .noneMatch(a -> a.getSecurityOld().getId().equals(parent.getId()));
  }

  @Test
  void booksRebuildsAndReversesThreeLegsAndProtectsDependentTransactions() throws Exception {
    Tenant tenant = testTenant();
    Account account = account(tenant, "Book");
    booking(account, parent, TransactionType.ACCUMULATE, 60, 50, EX_DATE.minusMonths(8));
    booking(account, parent, TransactionType.ACCUMULATE, 40, 70, EX_DATE.minusMonths(4));
    SecurityAction action = actionWithPrices();
    Integer id = action.getId();
    var preview = resource.previewSpinOff(id);
    assertThat(preview.accounts()).singleElement().satisfies(row -> {
      assertThat(row.parentUnits()).isEqualTo(100);
      assertThat(row.childUnits()).isEqualTo(25);
      assertThat(row.saleAmount() + row.repurchaseAmount() + row.childAmount()).isZero();
      assertThat(row.decision()).isEqualTo(Decision.BOOK);
    });
    var decision = new SpinOffDecisionDto(account.security().getId(), 100.0, Decision.BOOK);
    var applied = resource.applySecurityAction(id, List.of(decision)).getBody();
    assertThat(applied.getBookedAccounts()).isEqualTo(1);
    assertThat(applied.getSkippedAccounts()).isZero();
    assertThat(applied.getSecurityAction().getAppliedCount()).isEqualTo(1);
    List<Transaction> legs = transactions.findByIdSecurityActionApp(applied.getId());
    assertThat(legs).extracting(Transaction::getSecurityActionRole).containsExactlyInAnyOrder(
        SecurityActionRole.SPIN_OFF_SALE, SecurityActionRole.SPIN_OFF_REPURCHASE, SecurityActionRole.SPIN_OFF_CHILD);
    assertThat(legs).allMatch(t -> t.getConnectedIdTransaction() == null);

    em.flush();
    em.clear();
    rebuild.doWork(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), tenant.getId(), Tenant.class.getSimpleName()));
    em.flush();
    assertThat(balance.countConsistencyDefects(1e-6)).noneMatch(d -> d.getIdTenant().equals(tenant.getId()));
    assertThat(deposit.countConsistencyDefects(1e-6)).noneMatch(d -> d.getIdTenant().equals(tenant.getId()));
    assertThat(holdings.countConsistencyDefects(1e-6)).noneMatch(d -> d.getIdTenant().equals(tenant.getId()));

    resource.reverseSecurityAction(id);
    assertThat(transactions.findByIdSecurityActionApp(applied.getId())).isEmpty();
    var reapplied = resource.applySecurityAction(id, List.of(decision)).getBody();
    Transaction parentSale = booking(account, parent, TransactionType.REDUCE, 50, 70, EX_DATE);
    Transaction childSale = booking(account, action.getSecurityNew(), TransactionType.REDUCE, 10, 50, EX_DATE);
    assertThat(realized(tenant, parent, action.getSecurityNew()))
        .containsExactlyInAnyOrderEntriesOf(Map.of(parentSale.getId(), 1500.0, childSale.getId(), 100.0));

    SecurityAction correction = mapper.readValue(mapper.writeValueAsString(actions.findById(id).orElseThrow()),
        SecurityAction.class);
    correction.setCostAllocation(0.25);
    resource.correctSpinOff(id, correction);
    assertThat(realized(tenant, parent, action.getSecurityNew()))
        .containsExactlyInAnyOrderEntriesOf(Map.of(parentSale.getId(), 1625.0, childSale.getId(), 0.0));

    assertViolation(() -> resource.applySecurityAction(id, List.of(decision)), "gt.security.action.already.applied");
    assertViolation(() -> resource.reverseSecurityAction(id), "gt.security.action.spinoff.reverse.blocked");
    assertViolation(() -> transactions.deleteSingleDoubleTransaction(reapplied.getIdTransactionSell()),
        "gt.transaction.spinoff.leg.protected");
    Transaction currentLeg = transactions.findByIdSecurityActionApp(applied.getId()).getFirst();
    assertViolation(() -> transactions.saveOnlyAttributes(currentLeg, currentLeg, Set.of()),
        "gt.transaction.spinoff.leg.protected");
    Transaction backdated = new Transaction(account.security().getId(), account.cash(), parent, -10.0, 1.0, 10.0,
        TransactionType.ACCUMULATE, null, null, null, EX_DATE.minusDays(1).atStartOfDay(), null, null, null, null);
    backdated.setIdTenant(tenant.getId());
    assertViolation(() -> transactions.saveOnlyAttributes(backdated, null, Set.of()),
        "gt.transaction.spinoff.entitlement.changed");
  }

  @Test
  void proposesSkipForAManualPatternOrAnImportedDeliveryAndRejectsStaleUnits() throws Exception {
    Tenant tenant = testTenant();
    Account manual = account(tenant, "Manual");
    Account imported = account(tenant, "Imported child delivery");
    Account simple = account(tenant, "Unrelated child purchase");
    SecurityAction action = actionWithPrices();
    Security child = action.getSecurityNew();
    for (Account account : List.of(manual, imported, simple)) {
      booking(account, parent, TransactionType.ACCUMULATE, 100, 58, EX_DATE.minusMonths(4));
    }
    booking(manual, parent, TransactionType.REDUCE, 100, 76, EX_DATE);
    booking(manual, parent, TransactionType.ACCUMULATE, 100, 64, EX_DATE);
    booking(manual, child, TransactionType.ACCUMULATE, 25, 48, EX_DATE);
    booking(imported, child, TransactionType.ACCUMULATE, 25, 48, EX_DATE);
    booking(simple, child, TransactionType.ACCUMULATE, 10, 48, EX_DATE);
    var preview = resource.previewSpinOff(action.getId());
    assertThat(preview.accounts()).extracting(row -> row.idSecurityaccount() + ":" + row.decision())
        .containsExactly(manual.security().getId() + ":SKIP", imported.security().getId() + ":SKIP",
            simple.security().getId() + ":BOOK");
    assertViolation(
        () -> resource.applySecurityAction(action.getId(),
            List.of(new SpinOffDecisionDto(manual.security().getId(), 100.0, Decision.SKIP),
                new SpinOffDecisionDto(imported.security().getId(), 100.0, Decision.SKIP),
                new SpinOffDecisionDto(simple.security().getId(), 99.0, Decision.BOOK))),
        "gt.security.action.spinoff.preview.stale");
  }

  private Tenant testTenant() {
    Tenant tenant = new Tenant("Spin-off bookings", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    admin.setIdTenant(tenant.getId());
    return tenant;
  }

  private record Account(Securityaccount security, Cashaccount cash) {
  }

  private Account account(Tenant tenant, String name) {
    Portfolio portfolio = new Portfolio(tenant.getId(), name, "CHF");
    em.persist(portfolio);
    Cashaccount cash = new Cashaccount(name + " CHF", 0.0, "CHF", portfolio);
    cash.setIdTenant(tenant.getId());
    cash.setBorrowingRate(0.0);
    em.persist(cash);
    Securityaccount security = new Securityaccount(name, portfolio);
    security.setIdTenant(tenant.getId());
    security.setTradingPlatformPlan(em.createQuery("SELECT p FROM TradingPlatformPlan p", TradingPlatformPlan.class)
        .setMaxResults(1).getSingleResult());
    em.persist(security);
    return new Account(security, cash);
  }

  private SecurityAction actionWithPrices() throws Exception {
    Security child = equity("Child in spin-off account", "CH998765435");
    em.persist(child);
    em.persist(new Historyquote(parent.getId(), HistoryquoteCreateType.CONNECTOR_CREATED, EX_DATE, 64));
    em.persist(new Historyquote(child.getId(), HistoryquoteCreateType.CONNECTOR_CREATED, EX_DATE, 48));
    SecurityAction saved = resource.createSecurityAction(request(child)).getBody();
    assertViolation(() -> resource.previewSpinOff(saved.getId()), "gt.security.action.spinoff.refresh.pending");
    completeParentRefresh();
    return saved;
  }

  /** Stands in for the parent refresh, which needs the data providers; the action keeps its undetermined basis. */
  private void completeParentRefresh() {
    tasks.removeByIdTaskAndProgressStateType(TaskTypeExtended.SPIN_OFF_PARENT_REFRESH.getValue(),
        ProgressStateType.PROG_WAITING.getValue());
  }

  private boolean refreshWaiting() {
    return tasks.existsByIdTaskAndIdEntityAndProgressStateType(TaskTypeExtended.SPIN_OFF_PARENT_REFRESH.getValue(),
        parent.getId(), ProgressStateType.PROG_WAITING.getValue());
  }

  @Test
  void dropsTheProviderSplitOfARecordedSpinOffAndRestoresItAfterTheDeletion() throws Exception {
    Security child = equity("Child of a provider split", "CH998765436");
    em.persist(child);
    SecurityAction saved = resource.createSecurityAction(request(child)).getBody();
    assertThat(refreshWaiting()).isTrue();
    assertThat(saved.getHistoryBasis()).isEqualTo(SpinOffHistoryBasis.UNDETERMINED);
    completeParentRefresh();

    var importer = new SecuritysplitJpaRepositoryImpl();
    ReflectionTestUtils.setField(importer, "securityActionJpaRepository", actions);
    var providerSplit = new Securitysplit(parent.getId(), EX_DATE, 1000, 1281, CreateType.CONNECTOR_CREATED);
    var stockDividend = new Securitysplit(parent.getId(), EX_DATE.minusDays(20), 100, 101,
        CreateType.CONNECTOR_CREATED);
    var nextDay = new Securitysplit(parent.getId(), EX_DATE.plusDays(1), 3, 2, CreateType.CONNECTOR_CREATED);
    assertThat(ReflectionTestUtils.<List<Securitysplit>>invokeMethod(importer, "removeSplitsOfSpinOffs", parent,
        List.of(stockDividend, providerSplit, nextDay))).containsExactly(stockDividend, nextDay);
    em.flush();
    assertThat(actions.findById(saved.getId()).orElseThrow().getProviderSplitFactor()).isEqualTo(1.281);

    resource.deleteSecurityAction(saved.getId());
    assertThat(refreshWaiting()).isTrue();
    assertThat(ReflectionTestUtils.<List<Securitysplit>>invokeMethod(importer, "removeSplitsOfSpinOffs", parent,
        List.of(stockDividend, providerSplit))).containsExactly(stockDividend, providerSplit);
  }

  @Test
  void refusesPricingWhileALaterSpinOffHasNoHistoryBasisAndAcceptsTheAdministratorsSetting() throws Exception {
    Tenant tenant = testTenant();
    Account account = account(tenant, "History basis");
    booking(account, parent, TransactionType.ACCUMULATE, 300, 50, EX_DATE.minusMonths(4));
    Securitysplit realSplit = new Securitysplit(parent.getId(), EX_DATE, 1, 2, CreateType.ADD_MODIFIED_USER);
    realSplit.setCreateModifyTime(LocalDateTime.now());
    em.persist(realSplit);
    SecurityAction first = actionWithPrices();
    assertThat(resource.previewSpinOff(first.getId()).accounts()).singleElement()
        .satisfies(row -> assertThat(row.parentUnits()).isEqualTo(600));

    Security laterChild = equity("Later spin-off child", "CH998765437");
    em.persist(laterChild);
    SecurityAction laterRequest = request(laterChild);
    laterRequest.setActionDate(EX_DATE.plusDays(30));
    SecurityAction later = resource.createSecurityAction(laterRequest).getBody();
    completeParentRefresh();
    assertViolation(() -> resource.previewSpinOff(first.getId()), "gt.security.action.spinoff.history.undetermined");

    SecurityAction setting = mapper.readValue(mapper.writeValueAsString(later), SecurityAction.class);
    setting.setHistoryBasisOrigin(SpinOffHistoryBasisOrigin.ADMINISTRATOR);
    setting.setHistoryBasis(SpinOffHistoryBasis.ADJUSTED);
    setting.setHistoryFactor(1.0);
    assertViolation(() -> resource.correctSpinOff(later.getId(), setting), "gt.security.action.spinoff.history.factor");
    setting.setHistoryFactor(1.25);
    SecurityAction corrected = resource.correctSpinOff(later.getId(), setting);
    assertThat(corrected.getHistoryBasisOrigin()).isEqualTo(SpinOffHistoryBasisOrigin.ADMINISTRATOR);
    assertThat(corrected.isHistoryBasisApplied()).isFalse();
    assertThat(corrected.getNote()).endsWith(": history UNDETERMINED -> ADJUSTED 1.25");
    assertThat(refreshWaiting()).isTrue();
    completeParentRefresh();
    assertThat(resource.previewSpinOff(first.getId()).accounts()).hasSize(1);

    List<Historyquote> peerQuotes = List.of(
        new Historyquote(parent.getId(), HistoryquoteCreateType.CONNECTOR_CREATED, EX_DATE.plusDays(29), 60),
        new Historyquote(parent.getId(), HistoryquoteCreateType.CONNECTOR_CREATED, EX_DATE.plusDays(30), 50));
    assertThat(securities.acceptPeerHistoryquotes(parent, peerQuotes)).extracting(Historyquote::getDate)
        .containsExactly(EX_DATE.plusDays(30));
  }

  private Transaction booking(Account account, Security security, TransactionType type, double units, double price,
      LocalDate date) {
    double amount = units * price * (type == TransactionType.REDUCE ? 1 : -1);
    Transaction t = new Transaction(account.security().getId(), account.cash(), security, amount, units, price, type,
        null, null, null, date.atStartOfDay(), null, null, null, null);
    t.setIdTenant(account.security().getIdTenant());
    em.persist(t);
    return t;
  }

  private Map<Integer, Double> realized(Tenant tenant, Security parent, Security child) {
    LocalDate until = EX_DATE.plusDays(1);
    var market = new DateTransactionCurrencypairMap("CHF", until, List.of(), List.of(), true);
    var calculator = new RealizedGainCalculator(calc, "CHF", new HashMap<>(Map.of("CHF", 2)), 2);
    var ledger = Map.of(parent, transactions.findByIdTenantAndIdSecurity(tenant.getId(), parent.getId()), child,
        transactions.findByIdTenantAndIdSecurity(tenant.getId(), child.getId()));
    var report = calculator.calculate(ledger, Map.of(), until, market, CostBasisMethods.all(CostBasisMethod.FIFO),
        false, null);
    Map<Integer, Double> results = new HashMap<>();
    report.years.forEach(year -> year.positions.forEach(sale -> results.put(sale.idTransaction, sale.gainLoss)));
    return results;
  }

  private Integer application(SecurityAction action, boolean reversed) {
    Tenant tenant = new Tenant(reversed ? "Spin-off reversed" : "Spin-off active", "CHF", 0, TenantKindType.MAIN,
        false);
    em.persist(tenant);
    SecurityActionApplication application = new SecurityActionApplication();
    application.setSecurityAction(action);
    application.setIdTenant(tenant.getId());
    application.setAppliedTime(EX_DATE.atStartOfDay());
    application.setReversed(reversed);
    application.setCostAllocation(action.getCostAllocation());
    em.persist(application);
    return application.getId();
  }

  private SecurityAction request(Security child) {
    SecurityAction action = new SecurityAction();
    action.setActionType(SecurityActionType.SPIN_OFF);
    action.setSecurityOld(parent);
    action.setSecurityNew(child);
    action.setIsinNew(child.getIsin());
    action.setActionDate(EX_DATE);
    action.setFromFactor(4);
    action.setToFactor(1);
    action.setCostAllocation(0.20);
    action.setTaxTreatmentDe(SpinOffTaxTreatmentDe.COST_SPLIT);
    action.setNote("Original note");
    return action;
  }

  private Security equity(String name, String isinBase) throws Exception {
    Assetclass assetClass = em.createQuery("""
        SELECT a FROM Assetclass a WHERE a.categoryType = :type AND a.specialInvestmentInstrument = :instrument
        ORDER BY a.idAssetClass
        """, Assetclass.class).setParameter("type", AssetclassType.EQUITIES.getValue())
        .setParameter("instrument", SpecialInvestmentInstruments.DIRECT_INVESTMENT.getValue()).setMaxResults(1)
        .getSingleResult();
    Stockexchange exchange = em
        .createQuery("SELECT s FROM Stockexchange s ORDER BY s.idStockexchange", Stockexchange.class).setMaxResults(1)
        .getSingleResult();
    String isin = isinBase + new ISINCheckDigit().calculate(isinBase);
    Security security = new Security(name, "CHF", assetClass, exchange, EX_DATE.minusYears(1), EX_DATE.plusYears(50),
        null, "SPIN", isin);
    security.setLeverageFactor(1);
    security.setCreatedBy(0);
    return security;
  }

  private static void assertViolation(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String key) {
    assertThatThrownBy(action).isInstanceOfSatisfying(DataViolationException.class,
        e -> assertThat(e.getDataViolation().getFirst().getMessageKey()).isEqualTo(key));
  }
}
