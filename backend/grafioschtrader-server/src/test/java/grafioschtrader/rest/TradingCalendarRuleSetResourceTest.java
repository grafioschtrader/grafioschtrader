package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.ProposeChangeField;
import grafiosch.error.SecurityBreachError;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.test.start.GTforTest;

/**
 * Creates the trading calendar rule sets tagged {@code e2e='i'} in the generated JSON fixture through the REST API. The
 * fixture mirrors the {@code stockexchanges.csv} three-way partition: {@code 'd'} rows are seeded via
 * {@code V2__testdata.sql}, {@code 'i'} rows are created here, and {@code 'e'} rows are created by the Playwright spec.
 * The fixture is JSON rather than CSV because {@code rule_yaml} is multi-line YAML that a pipe-delimited CSV cannot hold.
 */
@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class TradingCalendarRuleSetResourceTest extends BaseIntegrationTest {

  private static final String FIXTURE = "/testdata/generated/trading_calendar_rule_set.json";

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  /**
   * The {@code 'i'} rows of the fixture (Austria/Australia). These carry their real YAML and no parent, so the create
   * exercises the full validation path against genuine rule data.
   */
  static Stream<RuleSetSeed> iRows() {
    try (InputStream is = TradingCalendarRuleSetResourceTest.class.getResourceAsStream(FIXTURE)) {
      if (is == null) {
        throw new IllegalStateException("Missing fixture " + FIXTURE + " - regenerate it with nv.bat");
      }
      RuleSetSeed[] all = new ObjectMapper().readValue(is, RuleSetSeed[].class);
      return Arrays.stream(all).filter(r -> "i".equals(r.e2e()));
    } catch (java.io.IOException e) {
      throw new UncheckedIOException("Unable to read " + FIXTURE, e);
    }
  }

  @Order(4)
  @ParameterizedTest
  @MethodSource("iRows")
  @DisplayName("Users create the 'i' trading calendar rule sets ('e' rows reserved for the Playwright test)")
  void createTest(RuleSetSeed row)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    TradingCalendarRuleSet ruleSet = new TradingCalendarRuleSet();
    ruleSet.setName(row.name());
    ruleSet.setMic(row.mic());
    ruleSet.setRuleYaml(row.ruleYaml());

    TradingCalendarRuleSet saved = authenticatedClient(RestTestHelper.getRadomUser())
        .post()
        .uri(RequestGTMappings.TRADINGCALENDARRULESET_MAP)
        .body(ruleSet)
        .exchange()
        .expectStatus().isOk()
        .expectBody(TradingCalendarRuleSet.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(saved);
    Assertions.assertThat(saved.getIdTradingCalendarRuleSet()).isGreaterThan(0);
    List<ProposeChangeField> pcf = RestTestHelper.getDiffPropertiesOfTwoObjects(ruleSet, saved);
    Assertions.assertThat(pcf).isEmpty();
  }

  @Test
  @Order(5)
  @DisplayName("Limited user can not delete a trading calendar rule set from another user")
  void deleteByIdTest() {
    TradingCalendarRuleSet[] ruleSets = authenticatedClient(RestTestHelper.LIMIT1)
        .get()
        .uri(RequestGTMappings.TRADINGCALENDARRULESET_MAP)
        .exchange()
        .expectStatus().isOk()
        .expectBody(TradingCalendarRuleSet[].class)
        .returnResult()
        .getResponseBody();

    assertNotNull(ruleSets);
    // A rule set is only deletable when no exchange uses it and no other set extends it; otherwise assertDeletable
    // throws a data violation before the ownership check. It must also belong to another user, because deleting one's
    // own record is allowed - so we exclude the sets owned by limit1. What is left triggers the security breach.
    Integer limit1Id = RestTestHelper.getUserByNickname(RestTestHelper.LIMIT1).idUser;
    Set<String> parentNames = Arrays.stream(ruleSets).map(TradingCalendarRuleSet::getNameExtendsRuleSet)
        .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    Optional<TradingCalendarRuleSet> deletable = Arrays.stream(ruleSets)
        .filter(rs -> rs.getUsedByExchanges() != null && rs.getUsedByExchanges() == 0)
        .filter(rs -> !parentNames.contains(rs.getName()))
        .filter(rs -> rs.getCreatedBy() != null && !rs.getCreatedBy().equals(limit1Id))
        .findFirst();
    Assertions.assertThat(deletable).isPresent();

    authenticatedClient(RestTestHelper.LIMIT1)
        .delete()
        .uri(RequestGTMappings.TRADINGCALENDARRULESET_MAP + "/" + deletable.get().getIdTradingCalendarRuleSet())
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class)
        .value(body -> Assertions.assertThat(body).contains(SecurityBreachError.class.getSimpleName()));
  }

  /**
   * One row of the generated fixture. {@code extendsName} is the name of the parent set (the self-FK resolved by nv.bat,
   * ids differ between databases); it is null for the {@code 'i'} rows. Unknown properties are ignored so the fixture
   * can carry extra fields without breaking the test.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record RuleSetSeed(String mic, String name, String ruleYaml, String extendsName, String e2e) {
  }

}
