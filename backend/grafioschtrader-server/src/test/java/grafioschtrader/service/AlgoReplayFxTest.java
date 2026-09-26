package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.*;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;

@DisplayName("Captured replay FX tariffs, economic charges and coverage")
class AlgoReplayFxTest {
  static final LocalDate DATE = LocalDate.of(2020, 6, 15);
  private final Currencypair pair = new Currencypair("USD", "CHF");
  private final List<AlgoReplayFx.Warning> warnings = new ArrayList<>();

  static FxFeeConfig flat(String expression) {
    return new FxFeeConfig(null, null, List.of(new FeeRule("flat", "true", expression)), null);
  }

  static AlgoReplayInputs.Snapshot snapshot(Map<Integer, FxFeeConfig> models) {
    return new AlgoReplayInputs.Snapshot(5, false, false, 0, Map.of(), Map.of(), Map.of(), List.of()).withFees(Map.of(),
        Map.of(), models);
  }

  private AlgoReplayFx fx(Map<Integer, FxFeeConfig> models) {
    return new AlgoReplayFx(snapshot(models), "CHF", (from, to, _) -> from.equals(to) ? 1.0 : .8, warnings::add);
  }

  @Test
  void repeatedEstimatesAndDuplicateCommitsDoNotInflateCharges() {
    var fx = fx(Map.of(1, flat("1")));
    FxQuote quote = null;
    for (int i = 0; i < 10; i++)
      quote = fx.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null);
    assertThat(fx.paid()).isZero();
    assertThat(fx.uncovered()).isZero();
    assertThat(warnings).isEmpty();
    assertThat(AlgoReplayFx.effective(pair, .8, "USD", quote)).isCloseTo(.808, within(1e-12));
    assertThat(AlgoReplayFx.effective(pair, .8, "CHF", quote)).isCloseTo(.792, within(1e-12));
    var buy = fx.conversion(1, "CHF", "USD", "TRADE", quote, .8, "CHF", DATE);
    fx.committed("buy", buy);
    fx.committed("buy", buy);
    fx.committed("sell", fx.conversion(1, "USD", "CHF", "TRADE", quote, .8, "CHF", DATE));
    fx.committed("transfer", fx.conversion(1, "USD", "CHF", "TRANSFER", quote, 1, "USD", DATE));
    assertThat(fx.paid()).isCloseTo(2.4, within(1e-12));
    assertThat(fx.details("buy", "reduced")).isEqualTo("reduced; fx=1.0% (flat)");
  }

  @Test
  void pairDirectionDeterminesTheAdverseSign() {
    var fx = fx(Map.of(1, flat("1")));
    var quote = fx.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null);
    var inverse = new Currencypair("CHF", "USD");
    assertThat(AlgoReplayFx.effective(inverse, 1.25, "USD", quote)).isCloseTo(1.2375, within(1e-12));
    assertThat(AlgoReplayFx.effective(inverse, 1.25, "CHF", quote)).isCloseTo(1.2625, within(1e-12));
  }

  @Test
  void coverageCountsCommittedConversionsAndWarnsOncePerKey() {
    var fx = fx(Map.of(1, flat("1")));
    var missing = fx.quote(2, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null);
    for (int i = 0; i < 3; i++)
      fx.committed("missing" + i, fx.conversion(2, "CHF", "USD", "TRADE", missing, 0, "CHF", DATE));
    assertThat(fx.uncovered()).isEqualTo(3);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst().details()).contains("2: CHF→USD TRADE NO_SECTION");
    var zero = fx(Map.of(1, flat("0")));
    var known = zero.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null);
    zero.committed("zero", zero.conversion(1, "CHF", "USD", "TRADE", known, 0, "CHF", DATE));
    assertThat(zero.uncovered()).isZero();
    var absent = fx(Map.of());
    absent.committed("mid", absent.conversion(2, "CHF", "USD", "TRADE", missing, 0, "CHF", DATE));
    assertThat(absent.uncovered()).isZero();
  }

  @Test
  void gapsAndMissingTierPricesRemainDistinctFromInvalidModels() {
    var period = new FxFeeConfig.Period("2021-01-01", null, FxFeeConfig.Status.VERIFIED, "fixture", null,
        flat("1").rules());
    var noPeriod = fx(Map.of(1, new FxFeeConfig(null, null, null, List.of(period))));
    assertThat(noPeriod.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null).outcome())
        .isEqualTo(FxOutcome.NO_PERIOD);
    var noRule = fx(
        Map.of(1, new FxFeeConfig(null, null, List.of(new FeeRule("EUR", "payCurrency = \"EUR\"", "1")), null)));
    assertThat(noRule.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null).outcome()).isEqualTo(FxOutcome.NO_RULE);
    var tier = new FxFeeConfig("EUR", null, List.of(new FeeRule("tier", "tierAmount > 0", "1")), null);
    var noRate = new AlgoReplayFx(snapshot(Map.of(1, tier)), "CHF", (_, _, _) -> null, warnings::add);
    assertThat(noRate.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null).outcome())
        .isEqualTo(FxOutcome.NO_TIER_RATE);
    for (String invalid : List.of("-1", "5", "unknownVariable"))
      assertThatThrownBy(() -> fx(Map.of(1, flat(invalid))).quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null))
          .isInstanceOf(AlgoReplayFx.Failure.class).hasMessageStartingWith("REPLAY_FX_MODEL_FAILED: 1:");
  }

  @Test
  void tiersUsePayCurrencyAndReceiveCurrencyWithoutAnExtraQuote() {
    var tier = new FxFeeConfig("USD", null,
        List.of(new FeeRule("tier", "tierAmount >= 100", "1"), new FeeRule("small", "true", "2")), null);
    var fx = new AlgoReplayFx(snapshot(Map.of(1, tier)), "CHF", (_, _, _) -> {
      throw new AssertionError("extra quote");
    }, warnings::add);
    assertThat(fx.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null).percent()).isEqualTo(1);
    assertThat(fx.quote(1, "USD", "CHF", pair, .8, "TRADE", 100, DATE, null).percent()).isEqualTo(1);
    assertThat(fx.quote(1, "CHF", "USD", pair, .8, "TRADE", 79, DATE, null).percent()).isEqualTo(2);
  }

  @Test
  void captureFreezesInheritedAndOverriddenSectionsAndOldVersionsStayAtMid() {
    var plan = new TradingPlatformPlan();
    plan.setFeeModelYaml(
        "rules:\n  - name: commission\n    condition: 'true'\n    expression: '0'\nfx:\n  rules:\n    - name: markup\n      condition: 'true'\n      expression: '1'\n");
    var inherited = new Securityaccount();
    inherited.setIdSecuritycashAccount(1);
    inherited.setTradingPlatformPlan(plan);
    var override = new Securityaccount();
    override.setIdSecuritycashAccount(2);
    override.setTradingPlatformPlan(plan);
    override.setFeeModelYaml("fx:\n  rules:\n    - name: zero\n      condition: 'true'\n      expression: '0'\n");
    var captured = AlgoReplayCustodyService.capture(snapshot(Map.of()), List.of(inherited, override), List.of(), null,
        DATE, DATE.plusYears(1));
    plan.setFeeModelYaml("broken after capture");
    var restored = AlgoReplayInputs.read(AlgoReplayInputs.write(captured));
    var fx = new AlgoReplayFx(restored, "CHF", (_, _, _) -> .8, warnings::add);
    assertThat(restored.version()).isEqualTo(5);
    assertThat(fx.quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null).percent()).isEqualTo(1);
    assertThat(fx.quote(2, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null).percent()).isZero();
    var old = AlgoReplayInputs.read(AlgoReplayInputs.write(captured).replace("\"version\":5", "\"version\":4"));
    assertThat(AlgoReplayFx.convention(old)).isEqualTo("FX_AT_EOD_MID");
    assertThat(new AlgoReplayFx(old, "CHF", (_, _, _) -> .8, warnings::add)
        .quote(1, "CHF", "USD", pair, .8, "TRADE", 80, DATE, null).percent()).isZero();
  }
}
