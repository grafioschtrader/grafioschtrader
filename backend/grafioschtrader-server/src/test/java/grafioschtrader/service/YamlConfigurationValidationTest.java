package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.common.StrictYaml;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.service.YamlConfigurationValidation.Format;

/** Pure validation/write-boundary tests; deliberately no Spring context or database. */
class YamlConfigurationValidationTest {
  private static final String FEE = "rules: [{name: Flat, condition: 'true', expression: '5'}]";
  private static final String TOKEN = "seed: {url: 'https://example.test', regex: '(ticket)'}\nlogin: {url: 'https://example.test', body: '{seedValue}', jwtPath: token}\n";

  @ParameterizedTest
  @ValueSource(strings = { "key: [", "key: 1\nkey: 2", "key: 1\n---\nkey: 2", "key: 1\n...\n[",
      "key: &a [1]\nother: *a" })
  void rejectsEntireInvalidDocument(String yaml) {
    assertThatThrownBy(() -> StrictYaml.validate(yaml)).isInstanceOf(StrictYaml.InvalidYamlException.class);
    for (Format format : Format.values())
      assertThat(YamlConfigurationValidation.validate(format, yaml, false)).isNotEmpty();
  }

  @Test
  void acceptsSupportedConfigurationsAndDefaults() {
    assertThat(YamlConfigurationValidation.validate(Format.FEES, FEE, true)).isEmpty();
    assertThat(YamlConfigurationValidation.validate(Format.TOKENS, TOKEN, true)).isEmpty();
    assertThat(YamlConfigurationValidation.validate(Format.CUSTODY,
        "'My account': {accruedFees: 5, assumption: 'Opening'}", true)).isEmpty();
    for (Format format : Format.values())
      assertThat(YamlConfigurationValidation.validate(format, "", false)).as(format.name()).isEmpty();
    assertThat(YamlConfigurationValidation.validate(Format.STRATEGY, "", true)).isNotEmpty();
  }

  @Test
  void checksSchemaAndExpressionsWithoutEchoingTokens() {
    assertThat(YamlConfigurationValidation.validate(Format.FEES, FEE.replace("'5'", "'MAX('"), true)).isNotEmpty();
    assertThat(YamlConfigurationValidation.validate(Format.TOKENS, TOKEN.replace("(ticket)", "ticket"), true))
        .isNotEmpty();
    assertThat(YamlConfigurationValidation.validate(Format.TOKENS, TOKEN + "secret: confidential", true).toString())
        .doesNotContain("confidential");
    assertThat(YamlConfigurationValidation.validate(Format.CUSTODY, "Account: {typo: 5}", true)).isNotEmpty();
  }

  @Test
  void invalidSaveCannotReachRepositoryOrMutateExistingEntities() {
    var plan = new TradingPlatformPlan();
    plan.setFeeModelYaml("rules: [");
    var previousPlan = new TradingPlatformPlan();
    previousPlan.setFeeModelYaml(FEE);
    assertThatThrownBy(
        () -> new TradingPlatformPlanJpaRepositoryImpl().saveOnlyAttributes(plan, previousPlan, Set.of()))
            .isInstanceOf(DataViolationException.class);
    assertThat(previousPlan.getFeeModelYaml()).isEqualTo(FEE);
    var account = new Securityaccount();
    account.setFeeModelYaml("rules: [");
    assertThatThrownBy(() -> new SecurityaccountJpaRepositoryImpl().saveOnlyAttributes(account, account, Set.of()))
        .isInstanceOf(DataViolationException.class);
    var connector = new GenericConnectorDef();
    connector.setTokenConfigYaml("seed: [");
    var previousConnector = new GenericConnectorDef();
    previousConnector.setTokenConfigYaml(TOKEN);
    assertThatThrownBy(
        () -> new GenericConnectorDefJpaRepositoryImpl().saveOnlyAttributes(connector, previousConnector, Set.of()))
            .isInstanceOf(DataViolationException.class);
    assertThat(previousConnector.getTokenConfigYaml()).isEqualTo(TOKEN);
  }
}
