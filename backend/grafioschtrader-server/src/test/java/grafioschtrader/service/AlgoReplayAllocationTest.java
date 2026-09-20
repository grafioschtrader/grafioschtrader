package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.*;
import grafioschtrader.types.SpecialInvestmentInstruments;

@DisplayName("Replay redistributes excluded allocations without editing the hierarchy")
class AlgoReplayAllocationTest {
  @Test
  void mixedClassFirstAndWholeClassSecondPreserveTopAndSource() {
    var top = new AlgoTop();
    top.setIdAlgoAssetclassSecurity(1);
    top.setPercentage(80f);
    var first = bucket(2, 40);
    var second = bucket(3, 40);
    var excluded = bucket(4, 20);
    var ordinary = member(20, 25, 1);
    var inverse = member(21, 75, -1);
    var zero = member(22, 0, 1);
    var other = member(30, 100, 1);
    var leveraged = member(40, 100, 2);
    Map<Integer, List<AlgoSecurity>> children = Map.of(2, List.of(ordinary, inverse, zero), 3, List.of(other), 4,
        List.of(leveraged));
    var allocation = AlgoReplayAllocation.capture(top, List.of(first, second, excluded), b -> children.get(b.getId()),
        Security::isSimulationTradingExcluded);
    assertThat(allocation.classes()).containsExactlyInAnyOrderEntriesOf(Map.of(2, 50f, 3, 50f));
    assertThat(allocation.members()).containsExactlyInAnyOrderEntriesOf(Map.of(20, 100f, 22, 0f, 30, 100f));
    assertThat(allocation.top(top).getPercentage()).isEqualTo(80f);
    assertThat(allocation.buckets(List.of(first, second, excluded))).extracting(AlgoAssetclass::getPercentage)
        .containsExactly(50f, 50f);
    assertThat(allocation.securities(children.get(2))).extracting(AlgoSecurity::getPercentage).containsExactly(100f,
        0f);
    assertThat(first.getPercentage()).isEqualTo(40f);
    assertThat(ordinary.getPercentage()).isEqualTo(25f);
    assertThat(top.getPercentage()).isEqualTo(80f);
    assertThatThrownBy(() -> allocation.classes().put(99, 5f)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void allExcludedCannotSilentlyBecomeCash() {
    var top = new AlgoTop();
    top.setPercentage(80f);
    assertThatThrownBy(() -> AlgoReplayAllocation.capture(top, List.of(bucket(2, 100)),
        _ -> List.of(member(20, 100, -1)), Security::isSimulationTradingExcluded))
            .hasMessage("REPLAY_NO_PERMITTED_ALLOCATION");
    assertThatThrownBy(() -> AlgoReplayAllocation.capture(top, List.of(bucket(2, 50), bucket(3, 50)),
        b -> b.getId() == 2 ? List.of(member(20, 100, -1)) : List.of(member(30, 0, 1)),
        Security::isSimulationTradingExcluded)).hasMessage("REPLAY_NO_PERMITTED_ALLOCATION");
  }

  @Test
  void inputsRoundTripPreservesFrozenLeverageAndEffectiveWeights() {
    var security = member(20, 100, -1).getSecurity();
    var instrument = new AlgoReplayInputs.Instrument("CHF", "ETF", null, null, null, null, false, null, null, "STORED",
        null, List.of(), List.of(), null, null);
    var effective = new AlgoReplayAllocation(1, 80f, Map.of(2, 100f), Map.of(20, 100f), Map.of(), Map.of());
    var inputs = new AlgoReplayInputs.Snapshot(3, false, false, 0, Map.of(), Map.of(), Map.of(20, instrument),
        List.of(), Map.of(20, -1f), effective);
    var restored = AlgoReplayInputs.read(AlgoReplayInputs.write(inputs));
    security.setLeverageFactor(1);
    assertThat(restored.excluded(20)).isTrue();
    assertThat(restored.allocation()).isEqualTo(effective);
  }

  private static AlgoAssetclass bucket(int id, float weight) {
    var bucket = new AlgoAssetclass();
    bucket.setIdAlgoAssetclassSecurity(id);
    bucket.setPercentage(weight);
    return bucket;
  }

  private static AlgoSecurity member(int id, float weight, float leverage) {
    var member = new AlgoSecurity();
    member.setIdAlgoAssetclassSecurity(id);
    member.setPercentage(weight);
    var security = new Security();
    security.setIdSecuritycurrency(id);
    security.setLeverageFactor(leverage);
    var asset = new Assetclass();
    asset.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.ETF);
    security.setAssetClass(asset);
    member.setSecurity(security);
    return member;
  }
}
