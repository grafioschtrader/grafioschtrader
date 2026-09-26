package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

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
  private static final LocalDate OPENING = LocalDate.of(2020, 2, 6);

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
  @DisplayName("Selling a position held on the opening day closes a round trip measured from its opening value")
  void openingPositionIsContinued() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.open(null, SECURITY, 300, 50, OPENING);
    trips.add(null, SECURITY, TransactionType.ACCUMULATE, 250, 40, 5.0, null, null, OPENING.plusDays(10));
    trips.add(null, SECURITY, TransactionType.REDUCE, 550, 60, 5.0, null, null, OPENING.plusDays(20));
    assertThat(trips.closedTrades()).singleElement().extracting(AlgoReplayMetrics.Trade::realizedGain)
        .as("-15000 -10000 +33000 less two commissions").isEqualTo(7990.0);
  }

  @Test
  @DisplayName("A split between the fills does not leave a closed position with a remainder")
  void splitBetweenFills() {
    LocalDate split = OPENING.plusDays(5);
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.useUnitBasis((_, date) -> date.isBefore(split) ? 5 : 1);
    trips.open(null, SECURITY, 600, 100, OPENING);
    trips.add(null, SECURITY, TransactionType.REDUCE, 2000, 21, null, null, null, split);
    assertThat(trips.closedTrades()).as("1000 of 3000 units in the split basis remain").isEmpty();
    trips.add(null, SECURITY, TransactionType.REDUCE, 1000, 22, null, null, null, split.plusDays(1));
    assertThat(trips.closedTrades()).singleElement().extracting(AlgoReplayMetrics.Trade::realizedGain)
        .as("-60000 +42000 +22000").isEqualTo(4000.0);
  }

  @Test
  @DisplayName("An opening position that is never sold, or nothing held at all, closes no round trip")
  void openingPositionStillHeld() {
    AlgoReplayRoundTrips trips = new AlgoReplayRoundTrips();
    trips.open(null, SECURITY, 100, 50, OPENING);
    trips.open(null, SECURITY + 1, 0, 50, OPENING);
    trips.add(null, SECURITY, TransactionType.REDUCE, 40, 55, null, null, null, OPENING.plusDays(1));
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
