package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop;
import grafioschtrader.entities.AlgoAssetclass;

/**
 * Funding of a triggered purchase out of the overweight classes, with the numbers of a fully invested portfolio whose
 * overweight is spread over several classes that each stay inside the tolerance of 8 percentage points.
 */
class AlgoRebalancingFundingTest {

  private static final double EQUITY = 100_000;
  private final RebalancingTop config = config();

  @Test
  @DisplayName("An underweight beyond the tolerance is paid for by the overweight classes in proportion")
  void underweightIsFundedProportionallyFromOverweights() {
    // Bonds 30 % target, 20 % held: triggered. Equities +6, commodities +4: both inside the tolerance.
    var inputs = List.of(input(1, 30, 20_000), input(2, 30, 36_000), input(3, 20, 24_000), input(4, 20, 20_000));
    var sales = AlgoRebalancingService.fundingSales(inputs, EQUITY, EQUITY, config, Set.of(), 0);
    assertThat(sales).containsOnlyKeys(2, 3);
    assertThat(sales.get(2)).isCloseTo(6_000, within(1e-6));
    assertThat(sales.get(3)).isCloseTo(4_000, within(1e-6));
  }

  @Test
  @DisplayName("Cash is spent first; only the part it cannot cover is sold")
  void cashCoversPartOfThePurchase() {
    var inputs = List.of(input(1, 30, 20_000), input(2, 30, 34_000), input(3, 40, 42_000));
    var sales = AlgoRebalancingService.fundingSales(inputs, EQUITY, EQUITY, config, Set.of(), 4_000);
    assertThat(sales.get(2)).isCloseTo(4_000, within(1e-6));
    assertThat(sales.get(3)).isCloseTo(2_000, within(1e-6));
  }

  @Test
  @DisplayName("Nothing is sold when the cash suffices or no class is triggered")
  void noFundingWithoutShortfall() {
    var triggered = List.of(input(1, 30, 20_000), input(2, 70, 70_000));
    assertThat(AlgoRebalancingService.fundingSales(triggered, EQUITY, EQUITY, config, Set.of(), 10_000)).isEmpty();
    var inside = List.of(input(1, 30, 25_000), input(2, 70, 75_000));
    assertThat(AlgoRebalancingService.fundingSales(inside, EQUITY, EQUITY, config, Set.of(), 0)).isEmpty();
  }

  @Test
  @DisplayName("A class committed by the checkpoint counts as triggered although it is back inside its band")
  void committedClassIsFunded() {
    var inputs = List.of(input(1, 30, 25_000), input(2, 70, 75_000));
    var sales = AlgoRebalancingService.fundingSales(inputs, EQUITY, EQUITY, config, Set.of(1), 0);
    assertThat(sales.get(2)).isCloseTo(5_000, within(1e-6));
  }

  private static AlgoRebalancingService.BucketInput input(int id, double targetPercentage, double actual) {
    AlgoAssetclass bucket = new AlgoAssetclass();
    bucket.setIdAlgoAssetclassSecurity(id);
    return new AlgoRebalancingService.BucketInput(bucket, List.of(), false, targetPercentage, actual);
  }

  private static RebalancingTop config() {
    RebalancingTop config = new RebalancingTop();
    config.setThresholdPercentage(8);
    config.setTimePeriodPerYear(4);
    return config;
  }
}
