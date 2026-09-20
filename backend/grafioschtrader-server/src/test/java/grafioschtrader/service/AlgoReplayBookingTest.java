package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlgoReplayBookingTest {

  @Test
  @DisplayName("Ordinary replay orders are floored to whole units")
  void ordinaryOrdersUseWholeUnits() {
    double lot = AlgoReplayBooking.tradableLot(instrument(false, null));

    assertThat(lot).isEqualTo(1.0);
    assertThat(AlgoReplayBooking.tradableUnits(55.5555, lot)).isEqualTo(55.0);
    assertThat(AlgoReplayBooking.tradableUnits(-3.75, lot)).isEqualTo(3.0);
    assertThat(AlgoReplayBooking.tradableUnits(0.75, lot)).isZero();
  }

  @Test
  @DisplayName("Direct bond replay orders use denomination on GT's nominal-per-100 unit basis")
  void directBondOrdersUseDenomination() {
    double lot = AlgoReplayBooking.tradableLot(instrument(true, 5_000));

    assertThat(lot).isEqualTo(50.0);
    assertThat(AlgoReplayBooking.tradableUnits(149.99, lot)).isEqualTo(100.0);
    assertThat(AlgoReplayBooking.tradableUnits(49.99, lot)).isZero();
  }

  @Test
  @DisplayName("A missing or invalid bond denomination falls back to one stored unit")
  void missingBondDenominationUsesWholeUnits() {
    assertThat(AlgoReplayBooking.tradableLot(instrument(true, null))).isEqualTo(1.0);
    assertThat(AlgoReplayBooking.tradableLot(instrument(true, 0))).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Version one replay inputs without denomination remain readable")
  void oldSnapshotWithoutDenominationRemainsReadable() {
    String json = """
        {"version":1,"applyTaxModels":false,"generateBondCoupons":false,"dividendDelay":0,
         "countryModels":{},"accounts":{},"instruments":{"7":{"currency":"CHF","directBond":true,
         "observations":[],"splits":[]}}}
        """;

    AlgoReplayInputs.Instrument restored = AlgoReplayInputs.read(json).instruments().get(7);

    assertThat(restored.denomination()).isNull();
    assertThat(restored.activeFromDate()).isNull();
    assertThat(restored.activeToDate()).isNull();
    assertThat(AlgoReplayBooking.tradableLot(restored)).isEqualTo(1.0);
  }

  private AlgoReplayInputs.Instrument instrument(boolean directBond, Integer denomination) {
    return new AlgoReplayInputs.Instrument("CHF", null, null, null, null, null, directBond, denomination, null, null,
        null, List.of(), List.of(), null, null);
  }
}
