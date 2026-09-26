package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafioschtrader.dto.CustodyOpeningState;

/** Calendar and monetary examples exercise the engine without a Spring context or a database. */
class CustodyFeeEngineTest {
  private static final String BASE = """
      rules:
        - name: Commission
          condition: 'true'
          expression: '10'
      custody:
        periods:
          - validFrom: '2025-01-01'
            status: ASSUMED
            source: 'Unit-test tariff'
            note: 'Explicit test convention'
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

  private static final LocalDate OPEN = LocalDate.of(2025, 12, 31);
  private static final LocalDate END = LocalDate.of(2026, 12, 31);
  private static final CustodyOpeningState ZERO = new CustodyOpeningState(1, 0.0, 0.0, 0.0, "Explicit zero balances");

  private CustodyFeeEngine engine(String yaml) {
    return new CustodyFeeEngine(CustodyFeeEngine.parse(yaml), OPEN, END, ZERO);
  }

  @Test
  void postfinanceAdvanceChargeAndExpiringCreditsAreIndependent() {
    var engine = engine(BASE);
    LocalDate january = LocalDate.of(2026, 1, 1);
    assertThat(engine.bills(january, true)).isTrue();
    var bill = engine.bill(january, 2);
    assertThat(bill.total()).isEqualTo(18);
    // Neither an affordability estimate nor a failed attempt consumes anything.
    assertThat(engine.credit(january, 10, Map.of())).isEqualTo(10);
    assertThat(engine.credit(january, 10, Map.of())).isEqualTo(10);
    engine.settled(bill);
    assertThat(engine.bills(january, true)).isFalse();
    engine.consume(january, "fill-1", 10);
    engine.consume(january, "fill-1", 10);
    assertThat(engine.credit(january, 20, Map.of())).isEqualTo(8);
    engine.consume(january, "fill-2", 8);
    assertThat(engine.credit(january, 20, Map.of())).isZero();
    assertThat(engine.credit(LocalDate.of(2026, 4, 1), 50, Map.of())).isEqualTo(18);
    assertThat(engine.liability(january)).isZero();
  }

  @Test
  void midQuarterOpeningUsesOnlyExplicitRemainingCredits() {
    var opening = LocalDate.of(2026, 2, 15);
    var unstated = new CustodyFeeEngine(CustodyFeeEngine.parse(BASE), opening, END,
        new CustodyOpeningState(1, null, null, null, null));
    assertThat(unstated.credit(opening.plusDays(1), 30, Map.of())).isZero();
    var engine = new CustodyFeeEngine(CustodyFeeEngine.parse(BASE), opening, END,
        new CustodyOpeningState(1, 0.0, 3.5, 18.0, "Broker statement"));
    assertThat(engine.credit(opening.plusDays(1), 30, Map.of())).isEqualTo(3.5);
    assertThat(engine.credit(LocalDate.of(2026, 4, 1), 30, Map.of())).isEqualTo(18);
  }

  @Test
  void excludedCommissionDoesNotUseCredits() {
    var engine = engine(BASE.replace("creditEligibility: 'true'", "creditEligibility: 'instrument == \"ETF\"'"));
    assertThat(engine.credit(OPEN.plusDays(1), 20, Map.of("instrument", "FOREX"))).isZero();
  }

  private String assetTariff(String valuation, String amount) {
    return BASE.replace("valuation: NONE", "valuation: " + valuation + "\n      observationDay: 0")
        .replace("collection: START", "collection: END").replace("amount: '18'", "amount: '" + amount + "'")
        .replace("creditAmount: 18", "creditAmount: 0");
  }

  private List<CustodyFeeEngine.Holding> holdings(double value) {
    return List.of(new CustodyFeeEngine.Holding(value, "ETF", "EQUITIES", "CH0000000001", "CHF", "XSWX"));
  }

  @Test
  void monthlyObservationAndQuarterlySettlementIncludeVatAndUnpaidLiability() {
    var engine = engine(assetTariff("MONTHLY", "assetValue * 0.0023") + "      vatRate: 0.081\n");
    assertThat(engine.observes(LocalDate.of(2026, 1, 30))).isFalse();
    for (int month = 1; month <= 3; month++) {
      LocalDate day = LocalDate.of(2026, month, 1).plusMonths(1).minusDays(1);
      engine.observe(day, holdings(120000));
      engine.observe(day, holdings(120000)); // retry must not duplicate observation
    }
    var day = LocalDate.of(2026, 3, 31);
    assertThat(engine.liability(day)).isCloseTo(69 * 1.081, within(1e-8));
    var bill = engine.bill(day, 2);
    assertThat(bill.net()).isEqualTo(69);
    assertThat(bill.vat()).isEqualTo(5.59);
    assertThat(engine.liability(day)).isGreaterThan(0); // unsaved bill has no effect
    engine.settled(bill);
    assertThat(engine.liability(day)).isZero();
    assertThat(engine.liability(LocalDate.of(2026, 2, 28))).isCloseTo(46 * 1.081, within(1e-8));
  }

  @Test
  void cornertraderDailyTierExampleIsThirtySixBeforeVat() {
    var engine = engine(
        assetTariff("DAILY", "IF(assetValue <= 50000, 80, 200)").replace("ACTUAL_ACTUAL", "ACTUAL_360"));
    LocalDate start = LocalDate.of(2026, 1, 1);
    for (int i = 0; i < 90; i++)
      engine.observe(start.plusDays(i), holdings(i < 42 ? 30000 : 120000));
    assertThat(engine.bill(LocalDate.of(2026, 3, 31), 2).net()).isEqualTo(36);
  }

  @Test
  void minimumMaximumAndAnnualCapAreAppliedBeforeVat() {
    var engine = engine(assetTariff("PERIOD_END", "assetValue * 0.01")
        + "      minimum: 20\n      maximum: 50\n      annualCap: 70\n      vatRate: 0.081\n");
    var first = LocalDate.of(2026, 3, 31);
    engine.observe(first, holdings(100000));
    var bill = engine.bill(first, 2);
    assertThat(bill.total()).isEqualTo(54.05);
    engine.settled(bill);
    var second = LocalDate.of(2026, 6, 30);
    engine.observe(second, holdings(100000));
    assertThat(engine.bill(second, 2).net()).isEqualTo(20);
  }

  @Test
  void unsupportedHistoryAndUnresolvedTermsNeverBecomeZero() {
    assertThatThrownBy(() -> engine(BASE.replace("2025-01-01", "2026-01-02")))
        .hasMessageContaining("No custody tariff");
    assertThatThrownBy(() -> engine(BASE.replace("ASSUMED", "UNRESOLVED")))
        .hasMessageContaining("Unresolved custody tariff");
    assertThatThrownBy(() -> engine(BASE.replace("amount: '18'", "amount: '-1'")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void schemaAcceptsBothLegacyAndExtendedModelsAndRejectsOverlap() {
    var validator = new TransactionCostEvalExEstimator();
    assertThat(validator.validate(BASE.substring(0, BASE.indexOf("custody:")))).isEmpty();
    assertThat(validator.validate(BASE)).isEmpty();
    String duplicate = BASE + BASE.substring(BASE.indexOf("    - validFrom:"));
    assertThat(validator.validate(duplicate)).anyMatch(e -> e.contains("overlapping"));
    assertThat(validator.validate(BASE.replace("note: 'Explicit test convention'", "note: ''")))
        .anyMatch(e -> e.contains("explanation"));
  }

  @Test
  void aTariffMayStartOnTheFirstReplayedDayWithoutExtrapolatingIntoTheOpeningDate() {
    var engine = engine(BASE.replace("2025-01-01", "2026-01-01"));
    assertThat(engine.currency(OPEN)).isEqualTo("CHF");
    assertThat(engine.bill(OPEN.plusDays(1), 2).total()).isEqualTo(18);
  }

  @Test
  void periodEndAssessmentReplacesTheOpeningEstimateRatherThanAddingItTwice() {
    var engine = new CustodyFeeEngine(CustodyFeeEngine.parse(assetTariff("PERIOD_END", "200")),
        LocalDate.of(2026, 2, 1), END, new CustodyOpeningState(1, 12.0, 0.0, 0.0, "Opening accrual"));
    var end = LocalDate.of(2026, 3, 31);
    engine.observe(end, holdings(10000));
    assertThat(engine.bill(end, 2).net()).isEqualTo(50);
  }

  @Test
  void closureRequiresAConventionAndProratesTheFinalPeriodEndAssessment() {
    LocalDate close = LocalDate.of(2026, 2, 14); // 45 of 90 days
    var missing = engine(assetTariff("PERIOD_END", "200"));
    assertThatThrownBy(() -> missing.validateClosing(close)).hasMessageContaining("closingPolicy");
    var prorated = engine(assetTariff("PERIOD_END", "200") + "      closingPolicy: PRORATED\n");
    prorated.observe(close, holdings(10000), true);
    assertThat(prorated.bill(close, 2, true).net()).isEqualTo(25);
    var full = engine(assetTariff("PERIOD_END", "200") + "      closingPolicy: FULL_PERIOD\n");
    full.observe(close, holdings(10000), true);
    assertThat(full.bill(close, 2, true).net()).isEqualTo(50);
  }

  @Test
  void fixedArrearsAccrueWithoutRequiringSecurityPrices() {
    var engine = engine(
        BASE.replace("collection: START", "collection: END").replace("creditAmount: 18", "creditAmount: 0"));
    for (int day = 1; day <= 10; day++)
      engine.observe(LocalDate.of(2026, 1, day), List.of());
    assertThat(engine.liability(LocalDate.of(2026, 1, 10))).isCloseTo(2, within(1e-8));
  }

  @Test
  void creditsRejectAnEarlierFillAfterALaterFillHasSpentTheAllowance() {
    var engine = engine(BASE);
    engine.consume(LocalDate.of(2026, 1, 5), "later", 18);
    assertThatThrownBy(() -> engine.credit(LocalDate.of(2026, 1, 2), 10, Map.of()))
        .hasMessageContaining("chronologically");
    assertThat(engine.credit(LocalDate.of(2026, 4, 1), 10, Map.of())).isEqualTo(10);
  }

  private CustodyFeeEngine.Holding holding(double value, String mic) {
    return new CustodyFeeEngine.Holding(value, "ETF", "EQUITIES", "IE0000000001", "EUR", mic);
  }

  private String degiro(String exchangeCondition) {
    return assetTariff("PERIOD_END", "MIN(2.5 * exchangeCount, 0.0025 * accountValue)").replace("billingMonths: 3",
        "billingMonths: 12")
        + (exchangeCondition == null ? "" : "      exchangeCondition: '" + exchangeCondition + "'\n");
  }

  @Test
  void degiroConnectivityCountsExchangesHeldCarriedInOrTradedInTheYear() {
    var engine = engine(degiro(null));
    engine.carryIn(List.of(holding(0, "XNYS")));
    engine.traded(LocalDate.of(2026, 3, 2), "fill-1", Map.of("mic", "XNAS", "instrument", "ETF"));
    engine.traded(LocalDate.of(2026, 3, 2), "fill-1", Map.of("mic", "XLON", "instrument", "ETF"));
    var end = LocalDate.of(2026, 12, 31);
    engine.observe(end, List.of(holding(40000, "XAMS"), holding(30000, "XNAS")));
    // XNYS carried in and sold, XNAS traded and held, XAMS held; the repeated fill id adds nothing.
    assertThat(engine.bill(end, 2).net()).isEqualTo(7.5);
  }

  @Test
  void degiroConnectivityIsCappedByAccountValueAndHonoursExemptExchanges() {
    var small = engine(degiro(null));
    var end = LocalDate.of(2026, 12, 31);
    small.observe(end, List.of(holding(1000, "XAMS"), holding(500, "XNAS")));
    assertThat(small.bill(end, 2).net()).isEqualTo(3.75);
    var exempt = engine(degiro("mic != \"XAMS\""));
    exempt.observe(end, List.of(holding(40000, "XAMS"), holding(30000, "XNAS")));
    assertThat(exempt.bill(end, 2).net()).isEqualTo(2.5);
  }

  @Test
  void semiAnnualBillingSettlesAtTheEndOfJuneAndDecember() {
    var engine = engine(assetTariff("PERIOD_END", "assetValue * 0.01").replace("billingMonths: 3", "billingMonths: 6"));
    assertThat(engine.observes(LocalDate.of(2026, 3, 31))).isFalse();
    assertThat(engine.observes(LocalDate.of(2026, 6, 30))).isTrue();
    assertThat(engine.bills(LocalDate.of(2026, 6, 30), false)).isTrue();
    assertThat(engine.bills(LocalDate.of(2026, 9, 30), false)).isFalse();
    engine.observe(LocalDate.of(2026, 6, 30), holdings(100000));
    assertThat(engine.bill(LocalDate.of(2026, 6, 30), 2).net()).isEqualTo(500);
  }

  @Test
  void creditsAreIsolatedBetweenConcurrentSessions() {
    var first = engine(BASE);
    var second = engine(BASE);
    first.consume(OPEN.plusDays(1), "a", 18);
    assertThat(second.credit(OPEN.plusDays(1), 18, Map.of())).isEqualTo(18);
  }
}
