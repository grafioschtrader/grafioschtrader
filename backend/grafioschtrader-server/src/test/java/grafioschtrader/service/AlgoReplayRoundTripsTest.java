package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.types.TransactionType;

/**
 * A round trip is classified as a win or a loss by what it realized, and the transaction costs left the account just as
 * the price difference did. A trip that only looks profitable before its two commissions must therefore not be counted
 * as a win.
 */
@DisplayName("Round trips of a historical replay")
class AlgoReplayRoundTripsTest {

  private static final Integer SECURITY = 42;

  @Test
  @DisplayName("A trip without costs realizes the plain price difference")
  void withoutCosts() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 10, 100, null);
    trips.add(null, SECURITY, TransactionType.REDUCE, 10, 110, null);
    assertThat(trips.closedTrades()).singleElement().extracting(AlgoReplayMetrics.Trade::realizedGain).isEqualTo(100.0);
  }

  @Test
  @DisplayName("The cost of every fill is taken out of what the trip realized")
  void costsAreNetted() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 10, 100, 15.0);
    trips.add(null, SECURITY, TransactionType.REDUCE, 10, 110, 15.0);
    assertThat(trips.closedTrades()).singleElement().extracting(AlgoReplayMetrics.Trade::realizedGain)
        .as("100 gross less the two commissions").isEqualTo(70.0);
  }

  @Test
  @DisplayName("Costs can turn a trip that was profitable on price alone into a loss")
  void costsCanTurnAWinIntoALoss() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 10, 100, 30.0);
    trips.add(null, SECURITY, TransactionType.REDUCE, 10, 102, 30.0);
    assertThat(trips.closedTrades()).singleElement().extracting(AlgoReplayMetrics.Trade::realizedGain)
        .as("20 gross against 60 of commission").isEqualTo(-40.0);
  }

  @Test
  @DisplayName("Every fill of a lifecycle contributes its cost, not only the two that open and close it")
  void costOfEveryFillCounts() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 10, 100, 10.0);
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 10, 90, 10.0);
    trips.add(null, SECURITY, TransactionType.REDUCE, 5, 95, 10.0);
    assertThat(trips.closedTrades()).as("the position is not flat yet").isEmpty();
    trips.add(null, SECURITY, TransactionType.REDUCE, 15, 105, 10.0);
    assertThat(trips.closedTrades()).singleElement().extracting(AlgoReplayMetrics.Trade::realizedGain)
        .as("-1000 -900 +475 +1575 less four commissions").isEqualTo(110.0);
  }

  @Test
  @DisplayName("A position still open on the end date contributes no round trip at all")
  void openPositionIsNoTrip() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 10, 100, 10.0);
    assertThat(trips.closedTrades()).isEmpty();
  }

  @Test
  @DisplayName("A strategy keeps a lifecycle of its own, and so does the rebalancing")
  void lifecyclesAreSeparate() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.add(7, SECURITY, TransactionType.ACCUMULATE, 10, 100, 5.0);
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 10, 100, 5.0);
    assertThat(trips.closedTrades()).as("neither lifecycle returned to flat").isEmpty();
    trips.add(7, SECURITY, TransactionType.REDUCE, 10, 120, 5.0);
    assertThat(trips.closedTrades()).singleElement().extracting(AlgoReplayMetrics.Trade::realizedGain).isEqualTo(190.0);
  }
}
