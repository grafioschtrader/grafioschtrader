package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;

@DisplayName("Account commission/custody ownership and independent FX inheritance")
class FeeModelResolverTest {
  static Securityaccount account(String own, String plan) {
    var sa = new Securityaccount();
    sa.setIdSecuritycashAccount(42);
    sa.setFeeModelYaml(own);
    var p = new TradingPlatformPlan();
    p.setFeeModelYaml(plan);
    sa.setTradingPlatformPlan(p);
    return sa;
  }

  @Test
  void completeResolutionMatrixAndUnsavedOverrideAgree() {
    String commission = FxMarkupEngineTest.COMMISSION;
    String fx = FxMarkupEngineTest.flat("1");
    String zero = FxMarkupEngineTest.flat("0");
    for (String[] row : new String[][] { { null, null, "NONE", "NONE" }, { null, commission, "PLAN", "NONE" },
        { null, commission + fx, "PLAN", "PLAN" }, { commission, commission + fx, "ACCOUNT", "PLAN" },
        { commission + zero, commission + fx, "ACCOUNT", "ACCOUNT" }, { fx, null, "NONE", "ACCOUNT" },
        { fx, commission + zero, "PLAN", "ACCOUNT" } }) {
      var sa = account(row[0], row[1]);
      var resolved = FeeModelResolver.resolve(sa);
      assertThat(resolved.commissionSource().name()).isEqualTo(row[2]);
      assertThat(resolved.fxSource().name()).isEqualTo(row[3]);
      var preview = FeeModelResolver.resolve(account("", row[1]), row[0]);
      assertThat(preview).usingRecursiveComparison().isEqualTo(resolved);
      assertThat(AlgoReplayFees.anyModelActive(List.of(sa))).isEqualTo(!row[2].equals("NONE"));
    }
    assertThat(FeeModelResolver.resolve(account(zero, commission + fx), "").fxSource())
        .isEqualTo(FeeModelResolver.Source.PLAN);
  }

  @Test
  void fxOnlyAccountInheritsCustodyWhileCommissionOverrideReplacesIt() throws Exception {
    String custody;
    try (var input = getClass().getResourceAsStream("/fee-models/custody/postfinance.yaml")) {
      custody = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    String plan = FxMarkupEngineTest.COMMISSION + custody + FxMarkupEngineTest.flat("1");
    var inherited = FeeModelResolver.resolve(account(FxMarkupEngineTest.flat("0"), plan));
    assertThat(inherited.commissionYaml()).isEqualTo(plan);
    assertThat(CustodyFeeEngine.parse(inherited.commissionYaml())).isNotNull();
    assertThat(
        CustodyFeeEngine.parse(FeeModelResolver.resolve(account(FxMarkupEngineTest.COMMISSION, plan)).commissionYaml()))
            .isNull();
    assertThatThrownBy(() -> FeeModelResolver.resolve(account(FxMarkupEngineTest.flat("0") + custody, plan)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
