package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.dto.AlgoTopReadiness;
import grafioschtrader.dto.AlgoTopReadiness.Issue;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** One small hierarchy per finding, checked without Spring or a database. */
class AlgoTopReadinessServiceTest {
  private static final String REBALANCING_CONFIG = """
      {"timePeriodPerYear":4,"thresholdPercentage":5,"securityDeviationPercentage":5.0,"maxTradedSecuritiesPerAssetclass":3}""";

  private final AlgoStrategyJpaRepository strategyRepository = mock(AlgoStrategyJpaRepository.class);
  private final WatchlistJpaRepository watchlists = mock(WatchlistJpaRepository.class);
  private final AlgoTopReadinessService service = new AlgoTopReadinessService(mock(AlgoAssetclassJpaRepository.class),
      strategyRepository, watchlists, mock(MessageSource.class));
  private final List<AlgoStrategy> strategies = new ArrayList<>();
  private AlgoTop top;
  private AlgoAssetclass bucket;

  @BeforeEach
  void setup() {
    top = new AlgoTop();
    top.setIdAlgoAssetclassSecurity(1);
    top.setIdTenant(7);
    top.setName("R");
    top.setPercentage(100f);
    bucket = bucket(2, 100f, member(3, 100f, 30));
    strategies.add(strategy(1, AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING, REBALANCING_CONFIG));
    when(strategyRepository.findByIdTenantAndIdAlgoAssetclassSecurityInOrderByIdAlgoRuleStrategy(anyInt(), any()))
        .thenReturn(strategies);
  }

  @Test
  @DisplayName("A complete hierarchy with a portfolio rebalance is ready for everything")
  void ready() {
    AlgoTopReadiness readiness = check(bucket);
    assertTrue(readiness.readyForReplay());
    assertTrue(readiness.readyForRebalancing());
    assertTrue(readiness.issues().isEmpty());
  }

  @Test
  @DisplayName("104% on the top level blocks both uses")
  void topWeightsIncomplete() {
    AlgoTopReadiness readiness = check(bucket, bucket(4, 4f, member(5, 100f, 31)));
    assertBlocked(readiness, AlgoTopReadinessService.WEIGHTS_INCOMPLETE, 1);
  }

  @Test
  @DisplayName("Securities of an asset class that do not add up to 100% block")
  void classWeightsIncomplete() {
    bucket.getAlgoSecurityList().getFirst().setPercentage(90f);
    assertBlocked(check(bucket), AlgoTopReadinessService.WEIGHTS_INCOMPLETE, 2);
  }

  @Test
  @DisplayName("A missing weighting or a maximum investment out of range blocks")
  void invalidWeights() {
    bucket.getAlgoSecurityList().getFirst().setPercentage(null);
    assertBlocked(check(bucket), AlgoTopReadinessService.WEIGHT_INVALID, 3);
    bucket.getAlgoSecurityList().getFirst().setPercentage(100f);
    top.setPercentage(null);
    assertBlocked(check(bucket), AlgoTopReadinessService.TOP_WEIGHT_INVALID, 1);
  }

  @Test
  @DisplayName("Without a portfolio rebalance a replay is possible, the comparison is not")
  void noRebalancing() {
    strategies.clear();
    AlgoTopReadiness readiness = check(bucket);
    assertTrue(readiness.readyForReplay());
    assertFalse(readiness.readyForRebalancing());
    assertEquals(List.of(AlgoTopReadinessService.REBALANCING_MISSING), codes(readiness));
  }

  @Test
  @DisplayName("An incomplete portfolio rebalance and an invalid class band block")
  void rebalancingConfiguration() {
    strategies.set(0, strategy(1, AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING, "{\"timePeriodPerYear\":4}"));
    assertBlocked(check(bucket), AlgoTopReadinessService.REBALANCING_CONFIG, 1);
    strategies.set(0, strategy(1, AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING, REBALANCING_CONFIG));
    bucket.setMaxTradedSecuritiesPerAssetclass(0);
    assertBlocked(check(bucket), AlgoTopReadinessService.BAND_INVALID, 2);
  }

  @Test
  @DisplayName("What the engines tolerate is reported without blocking")
  void tolerated() {
    bucket.getAlgoSecurityList().getFirst().getSecurity().setLeverageFactor(2);
    AlgoTopReadiness readiness = check(bucket);
    assertTrue(readiness.readyForRebalancing());
    assertEquals(List.of(AlgoTopReadinessService.INSTRUMENT_UNSUITABLE, AlgoTopReadinessService.ASSETCLASS_NO_INSTRUMENT),
        codes(readiness));
  }

  @Test
  @DisplayName("A security in two asset classes blocks only when a Mean Reversion Dip decides for it")
  void ambiguousSecurity() {
    top.setPercentage(100f);
    AlgoAssetclass second = bucket(4, 50f, member(5, 100f, 30));
    bucket.setPercentage(50f);
    AlgoTopReadiness readiness = check(bucket, second);
    assertTrue(readiness.readyForReplay());
    assertEquals(List.of(AlgoTopReadinessService.SECURITY_AMBIGUOUS), codes(readiness));

    strategies.add(strategy(3, AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP, "not json"));
    readiness = check(bucket, second);
    assertFalse(readiness.readyForReplay());
    Map<String, Boolean> blocking = readiness.issues().stream()
        .collect(Collectors.toMap(Issue::code, Issue::blocking, (a, _) -> a));
    assertTrue(blocking.get(AlgoTopReadinessService.SECURITY_AMBIGUOUS));
    assertTrue(blocking.get(AlgoTopReadinessService.MEAN_REVERSION_CONFIG));
    // No watchlist is linked, so the security cannot be on it.
    assertTrue(blocking.get(AlgoTopReadinessService.MEAN_REVERSION_WATCHLIST));
  }

  @Test
  @DisplayName("An inactive Mean Reversion Dip is not checked")
  void inactiveMeanReversion() {
    AlgoStrategy meanReversion = strategy(3, AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP,
        "not json");
    meanReversion.setActivatable(false);
    strategies.add(meanReversion);
    assertTrue(check(bucket).issues().isEmpty());
  }

  private AlgoTopReadiness check(AlgoAssetclass... buckets) {
    return service.check(top, List.of(buckets), Locale.ENGLISH);
  }

  private static void assertBlocked(AlgoTopReadiness readiness, String code, int idNode) {
    assertFalse(readiness.readyForReplay());
    assertFalse(readiness.readyForRebalancing());
    assertTrue(readiness.issues().getFirst().blocking());
    assertTrue(readiness.issues().stream()
        .anyMatch(issue -> issue.blocking() && code.equals(issue.code()) && issue.idNode() == idNode),
        () -> code + " on " + idNode + " not in " + codes(readiness));
  }

  private static List<String> codes(AlgoTopReadiness readiness) {
    return readiness.issues().stream().map(Issue::code).toList();
  }

  private static AlgoAssetclass bucket(int id, float percentage, AlgoSecurity... members) {
    AlgoAssetclass bucket = new AlgoAssetclass();
    bucket.setIdAlgoAssetclassSecurity(id);
    bucket.setName("Bucket " + id);
    bucket.setPercentage(percentage);
    bucket.setAlgoSecurityList(List.of(members));
    return bucket;
  }

  private static AlgoSecurity member(int id, float percentage, int idSecurity) {
    Security security = new Security();
    security.setIdSecuritycurrency(idSecurity);
    security.setName("Security " + idSecurity);
    security.setLeverageFactor(1);
    Assetclass instrument = new Assetclass();
    instrument.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(instrument);
    AlgoSecurity member = new AlgoSecurity();
    member.setIdAlgoAssetclassSecurity(id);
    member.setSecurity(security);
    member.setPercentage(percentage);
    return member;
  }

  private static AlgoStrategy strategy(int idNode, AlgoStrategyImplementationType type, String config) {
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoAssetclassSecurity(idNode);
    strategy.setAlgoStrategyImplementations(type);
    strategy.setStrategyConfig(config);
    return strategy;
  }
}
