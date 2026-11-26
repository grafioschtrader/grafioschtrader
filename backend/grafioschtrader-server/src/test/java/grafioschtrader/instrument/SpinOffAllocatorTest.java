package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityActionApplicationJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.SecurityActionRole;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

class SpinOffAllocatorTest {
  private static final LocalDate A = LocalDate.of(2008, 5, 2);
  private static final LocalDate B = LocalDate.of(2015, 3, 10);
  private static final LocalDate EX = LocalDate.of(2026, 3, 2);
  private static final LocalDate END = EX.plusDays(10);

  private final Security parent = equity(1, "USD");
  private final Security child = equity(2, "USD");
  private final SecurityCalcService calculator = new SecurityCalcService();
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final CurrencypairJpaRepository pairs = mock(CurrencypairJpaRepository.class);
  private final Map<String, Integer> precision = new HashMap<>(Map.of("USD", 2, "CHF", 2));
  private final DateTransactionCurrencypairMap dates = new DateTransactionCurrencypairMap(END, true);
  private final CostBasisMethods fifo = CostBasisMethods.all(CostBasisMethod.FIFO);

  @BeforeEach
  void setup() {
    SecurityActionApplicationJpaRepository apps = mock(SecurityActionApplicationJpaRepository.class);
    SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
    GlobalparametersService parameters = mock(GlobalparametersService.class);
    when(parameters.getCurrencyPrecision()).thenReturn(precision);
    when(parameters.getPrecisionForCurrency("USD")).thenReturn(2);
    when(parameters.getPrecisionForCurrency("CHF")).thenReturn(2);
    when(splits.getSecuritysplitMapByIdSecuritycurrency(1)).thenReturn(Map.of());
    when(splits.getSecuritysplitMapByIdSecuritycurrency(2)).thenReturn(Map.of());
    ReflectionTestUtils.setField(calculator, "spinOffApplications", apps);
    ReflectionTestUtils.setField(calculator, "spinOffTransactions", transactions);
    ReflectionTestUtils.setField(calculator, "spinOffSplits", splits);
    ReflectionTestUtils.setField(calculator, "spinOffPairs", pairs);
    ReflectionTestUtils.setField(calculator, "globalparametersService", parameters);
    SecurityAction action = new SecurityAction();
    action.setActionType(SecurityActionType.SPIN_OFF);
    action.setSecurityOld(parent);
    action.setSecurityNew(child);
    action.setFromFactor(4);
    action.setToFactor(1);
    action.setActionDate(EX);
    SecurityActionApplication application = new SecurityActionApplication();
    application.setIdSecurityActionApp(99);
    application.setSecurityAction(action);
    application.setCostAllocation(0.20);
    when(apps.findByIdTenant(1)).thenReturn(List.of(application));
    when(transactions.findByIdTenantAndIdSecurity(1, 1)).thenReturn(List.of(parentA(), parentB()));
    when(transactions.findByIdSecurityActionApp(99)).thenReturn(List.of(childLeg()));
  }

  @Test
  void laterSalesUseInheritedLotsInBothCombinedAndChildOnlyViews() {
    Transaction childSale = trade(child, EX.plusDays(1), 10, 50, 21, TransactionType.REDUCE);
    Transaction parentSale = trade(parent, EX.plusDays(1), 50, 70, 20, TransactionType.REDUCE);
    Transaction saleLeg = leg(parent, 10, 100, 76, SecurityActionRole.SPIN_OFF_SALE);
    Transaction repurchase = leg(parent, 11, 100, 64, SecurityActionRole.SPIN_OFF_REPURCHASE);
    var scope = calculator.spinOffContext(1, Set.of(1));

    var parentSummary = walk(parent, List.of(parentA(), parentB(), saleLeg, repurchase, parentSale), scope, fifo);
    var childSummary = walk(child, List.of(childLeg(), childSale), scope, fifo);
    var justChild = walk(child, List.of(childLeg(), childSale), calculator.spinOffContext(1, Set.of(1)), fifo);

    assertThat(parentSummary.transactionPositionList.get(2).transactionGainLoss).isNull();
    assertThat(parentSummary.transactionPositionList.getLast().transactionGainLoss).isEqualTo(1500.0);
    assertThat(childSummary.transactionPositionList.getLast().transactionGainLoss).isEqualTo(100.0);
    assertThat(justChild.transactionPositionList.getLast().transactionGainLoss).isEqualTo(100.0);
    assertThat(justChild.transactionPositionList.getLast().consumedLots).singleElement().satisfies(lot -> {
      assertThat(lot.transactionTime).isEqualTo(A.atStartOfDay());
      assertThat(lot.originIdTransaction).isEqualTo(1);
    });
  }

  @Test
  void parentSaleAndChildPurchaseReportTheMovedLotPartsForTheChart() {
    Transaction saleLeg = leg(parent, 10, 100, 76, SecurityActionRole.SPIN_OFF_SALE);

    var parentPosition = walk(parent, List.of(parentA(), parentB(), saleLeg), calculator.spinOffContext(1, Set.of(1)),
        fifo).securityPositionSummary;
    var childPosition = walk(child, List.of(childLeg()), calculator.spinOffContext(1, Set.of(1)),
        fifo).securityPositionSummary;

    assertThat(parentPosition.transactionSpinOffLots).extracting(lot -> lot.idTransaction, lot -> lot.cost)
        .containsExactly(tuple(1, 600.0), tuple(2, 560.0));
    assertThat(parentPosition.getCostLedger(1).getLots()).extracting(lot -> lot.cost).containsExactly(2400.0,
        2240.0);
    assertThat(childPosition.transactionSpinOffLots)
        .extracting(lot -> lot.idTransaction, lot -> lot.originIdTransaction, lot -> lot.cost)
        .containsExactly(tuple(12, 1, 600.0), tuple(12, 2, 560.0));
  }

  @Test
  void inheritedLotsAreInterleavedWithExistingChildPurchases() {
    Transaction childPurchase = trade(child, LocalDate.of(2010, 1, 1), 2, 15, 5, TransactionType.ACCUMULATE);
    Transaction sale = trade(child, EX.plusDays(1), 17, 50, 21, TransactionType.REDUCE);

    var summary = walk(child, List.of(childPurchase, childLeg(), sale), calculator.spinOffContext(1, Set.of(1)), fifo);

    assertThat(summary.transactionPositionList.getLast().transactionCostBase).isEqualTo(630.0);
    assertThat(summary.transactionPositionList.getLast().consumedLots).extracting(lot -> lot.idTransaction)
        .containsExactly(12, 5);
  }

  @Test
  void convertsInheritedCostIntoChildCurrency() {
    child.setCurrency("CHF");
    when(transactions.findByIdSecurityActionApp(99)).thenReturn(List.of(childLeg()));
    Currencypair pair = new Currencypair("CHF", "USD");
    when(pairs.findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency("USD", "CHF")).thenReturn(List.of(pair));
    when(pairs.getHistoricalClosePriceForDate(pair, EX)).thenReturn(1.25);

    var summary = walk(child, List.of(childLeg()), calculator.spinOffContext(1, Set.of(1)), fifo);

    assertThat(summary.securityPositionSummary.adjustedCostBase).isEqualTo(928.0);
    assertThat(summary.securityPositionSummary.getCostLedger(1).getLots()).extracting(lot -> lot.cost)
        .containsExactly(480.0, 448.0);
  }

  @Test
  void purchaseFeesAndRemainderMoveWithTheCostAndUncoveredUnitsAreFlagged() {
    var summary = new SecurityPositionSummary("USD", parent, precision);
    summary.addToLedger(parentA(), 60, 3030, null);
    summary.addToLedger(parentB(), 40, 2800, null);
    var feeAllocation = SpinOffAllocator.allocate(summary, childLeg(), CostBasisMethod.FIFO, 100, 25, 0.2, 1, 1.0, 2,
        2);
    assertThat(feeAllocation.childLots()).extracting(lot -> lot.cost).containsExactly(606.0, 560.0);

    var zeroCost = trade(parent, EX.minusDays(1), 4, 0, 3, TransactionType.ACCUMULATE);
    summary = new SecurityPositionSummary("USD", parent, precision);
    summary.addToLedger(parentA(), 60, 3000, null);
    summary.addToLedger(parentB(), 40, 2800, null);
    summary.addToLedger(zeroCost, 4, 0, null);
    var allocation = SpinOffAllocator.allocate(summary, childLeg(), CostBasisMethod.FIFO, 108, 27, 1.0 / 3, 1, 1.0, 2,
        2);

    assertThat(allocation.allocatedCost()).isEqualTo(1933.33);
    assertThat(allocation.childLots()).extracting(lot -> lot.cost).containsExactly(1000.0, 933.33, 0.0, 0.0);
    assertThat(allocation.childLots().get(2).incomplete).isFalse();
    assertThat(allocation.childLots().getLast()).satisfies(lot -> {
      assertThat(lot.units).isCloseTo(1.0, within(1e-10));
      assertThat(lot.incomplete).isTrue();
    });
    assertThat(allocation.parentLotsAfter().stream().mapToDouble(lot -> lot.cost).sum()).isCloseTo(3866.67,
        within(1e-8));
  }

  // 50 savings plan lots of 0.03 USD and 20 %: 0.30 USD move. Each lot's share of 0.006 must not be rounded on its
  // own, else 49 lots hand over 0.01 each and the last one -0.19, which leaves that parent lot at 0.22.
  @Test
  void manySmallLotsHandOverTheRoundedTotalWithoutANegativeLot() {
    var summary = new SecurityPositionSummary("USD", parent, precision);
    for (int i = 0; i < 50; i++) {
      summary.addToLedger(trade(parent, A.plusDays(i), 1, 0.03, 100 + i, TransactionType.ACCUMULATE), 1, 0.03, null);
    }

    var allocation = SpinOffAllocator.allocate(summary, childLeg(), CostBasisMethod.FIFO, 50, 12.5, 0.2, 1, 1.0, 2,
        2);

    assertThat(allocation.allocatedCost()).isEqualTo(0.30);
    assertThat(allocation.childLots()).extracting(lot -> lot.cost).allSatisfy(cost -> assertThat(cost).isIn(0.0, 0.01));
    assertThat(allocation.childLots().stream().mapToDouble(lot -> lot.cost).sum()).isCloseTo(0.30, within(1e-9));
    assertThat(allocation.childLots().subList(0, 30)).extracting(lot -> lot.cost).containsOnly(0.01);
    assertThat(allocation.parentLotsAfter()).extracting(lot -> lot.cost)
        .allSatisfy(cost -> assertThat(cost).isBetween(0.02 - 1e-9, 0.03 + 1e-9));
  }

  @Test
  void averageCostAllocatesTheAccountsProportionalShareOfTheScope() {
    var pooled = new SecurityPositionSummary("USD", parent, precision);
    pooled.units = 15;
    pooled.adjustedCostBase = 300;
    var separate = new SecurityPositionSummary("USD", parent, precision);
    separate.units = 5;
    separate.adjustedCostBase = 50;
    assertThat(average(pooled, 5)).isEqualTo(20);
    assertThat(average(pooled, 10)).isEqualTo(40);
    assertThat(average(separate, 5)).isEqualTo(10);
  }

  private double average(SecurityPositionSummary parent, double units) {
    return SpinOffAllocator
        .allocate(parent, childLeg(), CostBasisMethod.AVERAGE_COST, units, units / 4, 0.20, 1, 1.0, 2, 2)
        .allocatedCost();
  }

  private SecurityTransactionSummary walk(Security security, List<Transaction> txs, SpinOffContext context,
      CostBasisMethods methods) {
    var summary = new SecurityTransactionSummary(security, null, precision);
    calculator.calcTransactions(security, false, summary, Map.of(), txs, END, dates, methods, context);
    return summary;
  }

  private Transaction parentA() {
    return trade(parent, A, 60, 50, 1, TransactionType.ACCUMULATE);
  }

  private Transaction parentB() {
    return trade(parent, B, 40, 70, 2, TransactionType.ACCUMULATE);
  }

  private Transaction childLeg() {
    return leg(child, 12, 25, 48, SecurityActionRole.SPIN_OFF_CHILD);
  }

  private static Transaction leg(Security security, int id, double units, double quote, SecurityActionRole role) {
    var transaction = trade(security, EX, units, quote, id,
        role == SecurityActionRole.SPIN_OFF_SALE ? TransactionType.REDUCE : TransactionType.ACCUMULATE);
    transaction.setIdSecurityActionApp(99);
    transaction.setSecurityActionRole(role);
    return transaction;
  }

  private static Transaction trade(Security security, LocalDate on, double units, double quote, int id,
      TransactionType type) {
    Transaction t = new Transaction(1, new Cashaccount(1, security.getCurrency()), security, units, quote, type, null,
        null, null, on.atStartOfDay(), null);
    t.setIdTransaction(id);
    t.setIdTenant(1);
    return t;
  }

  private static Security equity(int id, String currency) {
    Security s = new Security();
    s.setIdSecuritycurrency(id);
    s.setCurrency(currency);
    Assetclass a = new Assetclass();
    a.setCategoryType(AssetclassType.EQUITIES);
    a.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    s.setAssetClass(a);
    return s;
  }
}
