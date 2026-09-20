package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.*;
import grafioschtrader.repository.*;

class AlgoAlertScopeResolverTest {
  private final AlgoAlertScopeResolver resolver = new AlgoAlertScopeResolver();
  private final AlgoAssetclassJpaRepository buckets = mock(AlgoAssetclassJpaRepository.class);
  private final AlgoSecurityJpaRepository members = mock(AlgoSecurityJpaRepository.class);
  private final AlgoStrategyJpaRepository strategies = mock(AlgoStrategyJpaRepository.class);
  private final WatchlistJpaRepository watchlists = mock(WatchlistJpaRepository.class);
  private final AlgoTop top = new AlgoTop();
  private final AlgoAssetclass bucket = new AlgoAssetclass();
  private final List<AlgoSecurity> children = new ArrayList<>();

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(resolver, "algoAssetclassJpaRepository", buckets);
    ReflectionTestUtils.setField(resolver, "algoSecurityJpaRepository", members);
    ReflectionTestUtils.setField(resolver, "algoStrategyJpaRepository", strategies);
    ReflectionTestUtils.setField(resolver, "watchlistJpaRepository", watchlists);
    top.setIdAlgoAssetclassSecurity(1);
    top.setIdTenant(65);
    top.setIdWatchlist(9);
    top.setActivatable(true);
    bucket.setIdAlgoAssetclassSecurity(2);
    bucket.setActivatable(true);
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(65, 1)).thenReturn(List.of(bucket));
    when(members.findByIdAlgoSecurityParentAndIdTenant(2, 65)).thenReturn(children);
    for (int id = 3; id < 201; id++) {
      Security security = new Security();
      security.setIdSecuritycurrency(id);
      AlgoSecurity member = new AlgoSecurity();
      member.setIdAlgoAssetclassSecurity(id);
      member.setSecurity(security);
      member.setActivatable(true);
      children.add(member);
    }
    when(watchlists.securitiesOfWatchlist(9)).thenReturn(List.of(children.getFirst().getSecurity()));
  }

  @Test
  void largeHierarchyReadsStrategiesOnceWithTenantAndNodeBounds() {
    when(strategies.findByIdTenantAndIdAlgoAssetclassSecurityInOrderByIdAlgoRuleStrategy(eq(65), anyCollection()))
        .thenReturn(List.of(strategy(10, 1), strategy(20, 2), strategy(30, 3)));

    var scopes = resolver.resolveForAlgoTop(top);

    assertThat(scopes).hasSize(200).allMatch(AlgoAlertScope::active);
    assertThat(scopes.getFirst().strategy().getId()).isEqualTo(10);
    assertThat(scopes.getLast().strategy().getId()).isEqualTo(30);
    List<Integer> nodeIds = new ArrayList<>(List.of(1, 2));
    children.forEach(child -> nodeIds.add(child.getId()));
    verify(strategies).findByIdTenantAndIdAlgoAssetclassSecurityInOrderByIdAlgoRuleStrategy(65, nodeIds);
    verifyNoMoreInteractions(strategies);
  }

  @Test
  void activationAndStrategyChangesRemainVisibleOnTheNextResolution() {
    when(strategies.findByIdTenantAndIdAlgoAssetclassSecurityInOrderByIdAlgoRuleStrategy(eq(65), anyCollection()))
        .thenReturn(List.of(strategy(20, 2), strategy(30, 3))).thenReturn(List.of(strategy(40, 3)));
    bucket.setActivatable(false);

    assertThat(resolver.resolveForAlgoTop(top)).hasSize(199).noneMatch(AlgoAlertScope::active);
    bucket.setActivatable(true);
    assertThat(resolver.resolveForAlgoTop(top)).singleElement().satisfies(scope -> {
      assertThat(scope.active()).isTrue();
      assertThat(scope.strategy().getId()).isEqualTo(40);
    });
    verify(strategies, times(2)).findByIdTenantAndIdAlgoAssetclassSecurityInOrderByIdAlgoRuleStrategy(eq(65),
        anyCollection());
  }

  private AlgoStrategy strategy(int id, int node) {
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoRuleStrategy(id);
    strategy.setIdAlgoAssetclassSecurity(node);
    strategy.setIdTenant(65);
    strategy.setActivatable(true);
    return strategy;
  }
}
