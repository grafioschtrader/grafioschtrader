package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetSupplierDetail;
import grafioschtrader.gtnet.GTNetExchangeKindType;

/**
 * Pure unit tests (no Spring context) for the OHL-aware supplier ranking of GitHub issue #172.
 *
 * The properties under test are the ones that make the preference safe to switch on: a zero weight must reproduce the
 * previous coverage x success-rate ordering byte for byte, an unknown OHL percentage must be neutral rather than a
 * penalty, instrument coverage must stay dominant over data richness, and equal-scoring suppliers must still be
 * shuffled so that load keeps spreading.
 */
class SupplierScoreCalculatorTest {

  private static final byte HIST = GTNetExchangeKindType.HISTORICAL_PRICES.getValue();

  @Test
  @DisplayName("A zero weight makes the OHL factor neutral for every percentage")
  void zeroWeightIsAlwaysNeutral() {
    SupplierScoreCalculator calculator = new SupplierScoreCalculator(null, 0.0);

    assertThat(calculator.getOhlFactor(100.0)).isEqualTo(1.0);
    assertThat(calculator.getOhlFactor(0.0)).isEqualTo(1.0);
    assertThat(calculator.getOhlFactor(null)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("An unknown OHL percentage is neutral, never a penalty")
  void unknownOhlIsNeutral() {
    SupplierScoreCalculator calculator = new SupplierScoreCalculator(null, 0.5);

    // A peer that has never been synchronised, an intraday peer and a currency pair all arrive as null here. They must
    // score exactly like a peer whose data is known to carry no OHL at all, not worse.
    assertThat(calculator.getOhlFactor(null)).isEqualTo(1.0);
    assertThat(calculator.getOhlFactor(0.0)).isEqualTo(1.0);
    assertThat(calculator.calculateScore(1, 10, null)).isEqualTo(10.0);
  }

  @Test
  @DisplayName("The OHL factor scales linearly with the reported percentage")
  void ohlFactorScalesWithPercentage() {
    SupplierScoreCalculator calculator = new SupplierScoreCalculator(null, 0.5);

    assertThat(calculator.getOhlFactor(0.0)).isEqualTo(1.0);
    assertThat(calculator.getOhlFactor(50.0)).isEqualTo(1.25);
    assertThat(calculator.getOhlFactor(100.0)).isEqualTo(1.5);
  }

  @Test
  @DisplayName("Percentages outside 0..100 are clamped instead of distorting the score")
  void ohlPercentageIsClamped() {
    SupplierScoreCalculator calculator = new SupplierScoreCalculator(null, 0.5);

    assertThat(calculator.getOhlFactor(140.0)).isEqualTo(1.5);
    assertThat(calculator.getOhlFactor(-20.0)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("A zero weight reproduces the previous coverage x success rate score")
  void zeroWeightReproducesLegacyScore() {
    List<Object[]> successRates = List.<Object[]>of(new Object[] { 1, 0.5 });
    SupplierScoreCalculator legacy = new SupplierScoreCalculator(successRates);
    SupplierScoreCalculator weighted = new SupplierScoreCalculator(successRates, 0.0);

    assertThat(legacy.calculateScore(1, 8)).isEqualTo(4.0);
    assertThat(weighted.calculateScore(1, 8, 100.0)).isEqualTo(4.0);
  }

  @Test
  @DisplayName("Wide coverage still beats a richer but narrower supplier")
  void coverageDominatesOhl() {
    // Broad supplier 1 carries 40 instruments close-only, narrow supplier 2 carries a single instrument with full OHL.
    List<GTNetSupplierDetail> details = new ArrayList<>();
    Set<Integer> requested = new LinkedHashSet<>();
    for (int instrumentId = 1; instrumentId <= 40; instrumentId++) {
      details.add(supplierDetail(1, instrumentId));
      requested.add(instrumentId);
    }
    details.add(supplierDetail(2, 1));

    List<Object[]> ohlRows = List.<Object[]>of(new Object[] { 2, 1, 100.0 });
    SupplierInstrumentFilter filter = new SupplierInstrumentFilter(details, ohlRows);
    SupplierScoreCalculator calculator = new SupplierScoreCalculator(null, 0.5);

    List<GTNet> sorted = calculator.sortSuppliersByScore(List.of(gtNet(1, (byte) 0), gtNet(2, (byte) 0)),
        GTNetExchangeKindType.HISTORICAL_PRICES, filter, requested);

    assertThat(sorted.get(0).getIdGtNet()).isEqualTo(1);
  }

  @Test
  @DisplayName("At equal coverage and reliability the OHL-rich supplier ranks first")
  void ohlRichSupplierWinsAtEqualCoverage() {
    List<GTNetSupplierDetail> details = List.of(supplierDetail(1, 10), supplierDetail(1, 11), supplierDetail(2, 10),
        supplierDetail(2, 11));
    Set<Integer> requested = new HashSet<>(List.of(10, 11));

    // Supplier 2 reports complete OHL, supplier 1 reports none.
    List<Object[]> ohlRows = List.<Object[]>of(new Object[] { 1, 10, 0.0 }, new Object[] { 1, 11, 0.0 },
        new Object[] { 2, 10, 100.0 }, new Object[] { 2, 11, 100.0 });
    SupplierInstrumentFilter filter = new SupplierInstrumentFilter(details, ohlRows);
    SupplierScoreCalculator calculator = new SupplierScoreCalculator(null, 0.5);

    List<GTNet> sorted = calculator.sortSuppliersByScore(List.of(gtNet(1, (byte) 0), gtNet(2, (byte) 0)),
        GTNetExchangeKindType.HISTORICAL_PRICES, filter, requested);

    assertThat(sorted.get(0).getIdGtNet()).isEqualTo(2);
    assertThat(sorted.get(1).getIdGtNet()).isEqualTo(1);
  }

  @Test
  @DisplayName("Suppliers with an equal score are still shuffled")
  void equalScoresAreStillShuffled() {
    List<GTNetSupplierDetail> details = List.of(supplierDetail(1, 10), supplierDetail(2, 10));
    Set<Integer> requested = new HashSet<>(List.of(10));
    List<Object[]> ohlRows = List.<Object[]>of(new Object[] { 1, 10, 80.0 }, new Object[] { 2, 10, 80.0 });

    SupplierInstrumentFilter filter = new SupplierInstrumentFilter(details, ohlRows);
    SupplierScoreCalculator calculator = new SupplierScoreCalculator(null, 0.5);
    List<GTNet> suppliers = List.of(gtNet(1, (byte) 0), gtNet(2, (byte) 0));

    Set<Integer> observedFirst = new HashSet<>();
    for (int run = 0; run < 200; run++) {
      List<GTNet> sorted = calculator.sortSuppliersByScore(suppliers, GTNetExchangeKindType.HISTORICAL_PRICES, filter,
          requested);
      observedFirst.add(sorted.get(0).getIdGtNet());
    }

    // Both suppliers must have led at least once; a continuous OHL factor must not silently pin the order.
    assertThat(observedFirst).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  @DisplayName("An average is taken only over instruments the supplier actually reported")
  void averageOhlIgnoresUnreportedInstruments() {
    List<GTNetSupplierDetail> details = List.of(supplierDetail(1, 10), supplierDetail(1, 11));
    // Only instrument 10 has a percentage; instrument 11 stays unknown and must not drag the average down.
    List<Object[]> ohlRows = List.<Object[]>of(new Object[] { 1, 10, 90.0 });
    SupplierInstrumentFilter filter = new SupplierInstrumentFilter(details, ohlRows);

    assertThat(filter.getAverageOhl(1, new HashSet<>(List.of(10, 11)))).isEqualTo(90.0);
    assertThat(filter.getAverageOhl(2, new HashSet<>(List.of(10)))).isNull();
  }

  @Test
  @DisplayName("A filter built without OHL rows reports every average as unknown")
  void filterWithoutOhlRowsIsUnknown() {
    SupplierInstrumentFilter filter = new SupplierInstrumentFilter(List.of(supplierDetail(1, 10)));

    assertThat(filter.getAverageOhl(1, new HashSet<>(List.of(10)))).isNull();
    assertThat(filter.getInstrumentsForSupplier(1, new HashSet<>(List.of(10, 11)), false)).containsExactly(10);
  }

  private GTNetSupplierDetail supplierDetail(Integer idGtNet, Integer idEntity) {
    GTNetConfig config = new GTNetConfig();
    config.setIdGtNet(idGtNet);
    GTNetSupplierDetail detail = new GTNetSupplierDetail();
    detail.setGtNetConfig(config);
    detail.setIdEntity(idEntity);
    detail.setEntityKind(HIST);
    return detail;
  }

  private GTNet gtNet(Integer idGtNet, byte consumerUsage) {
    GTNet gtNet = new GTNet();
    gtNet.setIdGtNet(idGtNet);
    GTNetEntity entity = new GTNetEntity();
    entity.setEntityKindValue(HIST);
    entity.getOrCreateConfigEntity().setConsumerUsage(consumerUsage);
    gtNet.setGtNetEntities(new ArrayList<>(List.of(entity)));
    return gtNet;
  }
}
