package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.dto.CustodyOpeningState;
import grafioschtrader.entities.*;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/** Verifies tenant/account boundaries and ordinary transaction settlement without touching a database. */
class AlgoReplayCustodyServiceTest {
  private static final LocalDate OPEN = LocalDate.of(2025, 12, 31);
  private static final LocalDate END = LocalDate.of(2026, 3, 31);
  /** The custody fragments shipped under resources/fee-models/custody and appended by the V0_37_1 migration. */
  private static final List<String> BROKERS = List.of("saxo", "postfinance", "migros", "migros-pension", "swissquote",
      "cornertrader");
  private static final String MODEL = """
      rules:
        - name: Trade
          condition: 'true'
          expression: '10'
      custody:
        periods:
          - validFrom: '2025-01-01'
            status: ASSUMED
            source: 'Test contract'
            note: 'Test settlement convention'
            currency: CHF
            valuation: NONE
            billingMonths: 3
            collection: START
            billingDay: 0
            dayCount: ACTUAL_ACTUAL
            amount: '18'
            creditAmount: 18
            creditEligibility: 'true'
      """;

  private Securityaccount account() {
    Portfolio portfolio = new Portfolio();
    portfolio.setIdPortfolio(20);
    Securityaccount account = new Securityaccount();
    account.setIdSecuritycashAccount(30);
    account.setName("Depot");
    account.setPortfolio(portfolio);
    TradingPlatformPlan plan = new TradingPlatformPlan();
    plan.setFeeModelYaml(MODEL);
    account.setTradingPlatformPlan(plan);
    return account;
  }

  private Cashaccount cash(Securityaccount account, String currency) {
    Cashaccount cash = new Cashaccount();
    cash.setIdSecuritycashAccount(40);
    cash.setPortfolio(account.getPortfolio());
    cash.setCurrency(currency);
    return cash;
  }

  private AlgoReplayInputs.Snapshot inputs() {
    return new AlgoReplayInputs.Snapshot(3, false, false, 0, Map.of(), Map.of(), Map.of(), List.of());
  }

  @Test
  void fxOnlyOverrideCapturesThePlansCommissionAndCustodyTogether() {
    var account = account();
    account.setFeeModelYaml(FxMarkupEngineTest.flat("0"));
    var snapshot = AlgoReplayCustodyService.capture(inputs(), List.of(account), List.of(cash(account, "CHF")), null,
        OPEN, END);
    assertThat(snapshot.feeModels().get(30)).isEqualTo(MODEL);
    assertThat(snapshot.custodyOpening().get(30).cashaccount()).isEqualTo(40);
  }

  @Test
  void freezesWholeAccountOverrideInsteadOfMergingPlan() {
    var account = account();
    account.setFeeModelYaml(MODEL.replace("amount: '18'", "amount: '5'"));
    var snapshot = AlgoReplayCustodyService.capture(inputs(), List.of(account), List.of(cash(account, "CHF")), null,
        OPEN, END);
    account.setFeeModelYaml(MODEL);
    assertThat(snapshot.feeModels().get(30)).contains("amount: '5'");
    assertThat(snapshot.custodyOpening().get(30).cashaccount()).isEqualTo(40);
    var restored = AlgoReplayInputs.read(AlgoReplayInputs.write(snapshot));
    assertThat(restored.feeModels()).isEqualTo(snapshot.feeModels());
    assertThat(restored.custodyOpening()).isEqualTo(snapshot.custodyOpening());
  }

  @Test
  void explicitFreeCustodyNeedsNoFeeCurrencyCashAccount() {
    var account = account();
    account
        .setFeeModelYaml(MODEL.replace("amount: '18'", "amount: '0'").replace("creditAmount: 18", "creditAmount: 0"));
    var snapshot = AlgoReplayCustodyService.capture(inputs(), List.of(account), List.of(), null, OPEN, END);
    assertThat(snapshot.custodyOpening().get(30).cashaccount()).isNull();
  }

  @Test
  void midCycleOpeningStartsWithoutAnyOpeningState() {
    var account = account();
    var cash = List.of(cash(account, "CHF"));
    LocalDate midCycle = LocalDate.of(2026, 2, 15);
    LocalDate end = LocalDate.of(2026, 5, 31);
    var none = AlgoReplayCustodyService.capture(inputs(), List.of(account), cash, null, midCycle, end).custodyOpening()
        .get(30);
    assertThat(none).isEqualTo(new CustodyOpeningState(40, 0.0, 0.0, 0.0, AlgoReplayCustodyService.ZERO_ASSUMED));
    var partial = AlgoReplayCustodyService
        .capture(inputs(), List.of(account), cash, "Depot: {accruedFees: 4.5}", midCycle, end).custodyOpening().get(30);
    assertThat(partial).isEqualTo(new CustodyOpeningState(40, 4.5, 0.0, 0.0, AlgoReplayCustodyService.ZERO_ASSUMED));
  }

  @Test
  void ambiguousAndForeignPortfolioSettlementAreRejectedBeforeRun() {
    var account = account();
    var first = cash(account, "CHF");
    var second = cash(account, "CHF");
    second.setIdSecuritycashAccount(41);
    assertThatThrownBy(
        () -> AlgoReplayCustodyService.capture(inputs(), List.of(account), List.of(first, second), null, OPEN, END))
            .hasMessageContaining("select one");
    Portfolio other = new Portfolio();
    other.setIdPortfolio(99);
    second.setPortfolio(other);
    String yaml = "Depot: {cashaccount: 41, accruedFees: 0, remainingCredits: 0, billedThisYear: 0, assumption: statement}";
    assertThatThrownBy(
        () -> AlgoReplayCustodyService.capture(inputs(), List.of(account), List.of(first, second), yaml, OPEN, END))
            .hasMessageContaining("select one");
  }

  @Test
  void booksOneFeeThroughNormalPathAndLeavesLiabilityUnchangedOnRejection() throws Exception {
    var account = account();
    var cash = cash(account, "EUR");
    var snapshot = inputs().withFees(Map.of(30, MODEL),
        Map.of(30, new CustodyOpeningState(40, 0.0, 0.0, 0.0, "Explicit opening state")));
    var state = mock(AlgoReplayState.class);
    var run = new AlgoSimulationResult();
    run.setIdSimulationResult(1);
    run.setOpeningDate(OPEN);
    run.setEndDate(END);
    ReflectionTestUtils.setField(state, "run", run);
    ReflectionTestUtils.setField(state, "inputs", snapshot);
    ReflectionTestUtils.setField(state, "market", mock(AlgoReplayMarketData.class));
    ReflectionTestUtils.setField(state, "decisionEquity", new HashMap<>());
    when(state.idTenant()).thenReturn(7);
    when(state.precision(anyString())).thenReturn(2);
    var valuation = mock(AlgoHistoricalValuationService.class);
    when(valuation.value(eq(7), any(), any())).thenReturn(new AlgoHistoricalValuationService.Snapshot("CHF", List.of(),
        Map.of(40, 100.0), Map.of("CHF", 1.0, "EUR", 0.9), 90, 0, List.of()));
    var transactions = mock(TransactionJpaRepository.class);
    when(transactions.saveOnlyAttributes(any(), isNull(), anySet()))
        .thenThrow(new IllegalArgumentException("Not funded"));
    var booking = mock(AlgoReplayBooking.class);
    var service = new AlgoReplayCustodyService(valuation, transactions, booking);
    var session = service.open(state, List.of(account), List.of(cash));
    var limit = new grafioschtrader.exceptions.TransactionLimitExceededException(10);
    doThrow(limit).when(booking).fundCustody(eq(state), eq(30), eq(cash), any(), anyDouble());
    assertThatThrownBy(() -> session.process(OPEN.plusDays(1), true)).isSameAs(limit);
    verifyNoInteractions(transactions);
    doNothing().when(booking).fundCustody(eq(state), eq(30), eq(cash), any(), anyDouble());
    doThrow(limit).when(transactions).throwWhenTransactionLimitReached(7, 1);
    assertThatThrownBy(() -> session.process(OPEN.plusDays(1), true)).isSameAs(limit);
    verify(transactions, never()).saveOnlyAttributes(any(), isNull(), anySet());
    doNothing().when(transactions).throwWhenTransactionLimitReached(7, 1);
    assertThatThrownBy(() -> session.process(OPEN.plusDays(1), true)).hasMessageContaining("Not funded");
    doAnswer(a -> {
      Transaction request = a.getArgument(0);
      assertThat(request.getCashaccountAmount()).isEqualTo(20); // FEE write contract
      request.setCashaccountAmount(-request.getCashaccountAmount());
      return request;
    }).when(transactions).saveOnlyAttributes(any(), isNull(), anySet());
    session.process(OPEN.plusDays(1), true);
    session.process(OPEN.plusDays(1), true);
    var captor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
    verify(transactions, times(2)).saveOnlyAttributes(captor.capture(), isNull(), anySet());
    var fee = captor.getValue();
    assertThat(fee.getTransactionType()).isEqualTo(TransactionType.FEE);
    assertThat(fee.getIdSecurityaccount()).isEqualTo(30);
    assertThat(fee.getIdTenant()).isEqualTo(7);
    assertThat(fee.getCashaccountAmount()).isEqualTo(-20);
    assertThat(session.liabilities(OPEN.plusDays(1)).get("CHF")).isZero();
  }

  @Test
  void brokerFragmentsRemainValid() throws Exception {
    var estimator = new TransactionCostEvalExEstimator();
    for (String broker : BROKERS) {
      assertThat(estimator.validate(brokerModel(broker))).as(broker).isEmpty();
    }
  }

  /**
   * Every shipped schedule must carry a replay across its whole history: no unresolved gap, and each tariff change on a
   * billing cycle boundary.
   */
  @Test
  void shippedBrokerFragmentsCoverLongHistoricalReplay() throws Exception {
    for (String broker : BROKERS) {
      var config = CustodyFeeEngine.parse(brokerModel(broker));
      assertThatCode(() -> new CustodyFeeEngine(config, LocalDate.of(2020, 1, 8), LocalDate.of(2026, 9, 20), null))
          .as(broker).doesNotThrowAnyException();
    }
  }

  private String brokerModel(String broker) throws Exception {
    try (var input = getClass().getResourceAsStream("/fee-models/custody/" + broker + ".yaml")) {
      return MODEL.substring(0, MODEL.indexOf("custody:"))
          + new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}
