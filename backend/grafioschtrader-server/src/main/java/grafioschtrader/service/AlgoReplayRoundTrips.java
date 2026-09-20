package grafioschtrader.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.service.AlgoReplayMetrics.Trade;
import grafioschtrader.types.TransactionType;

/**
 * Counts the round trips a replay closed, and whether each one earned or lost.
 *
 * <p>
 * A trade is a lifecycle rather than a fill: it begins when a position is opened from flat and ends when it returns to
 * flat, however many additions and partial exits happened in between. That is the only definition under which the
 * numbers add up for the modules this system has - an averaging down strategy would otherwise report every addition as
 * a trade of its own, and a profit taking plan would report a tranche as a completed trade while the position is still
 * open.
 * </p>
 *
 * <p>
 * The result is measured in the currency of the instrument, not of the environment. A round trip happens in one
 * instrument and therefore in one currency, so the exchange rate cannot turn a gain into a loss; leaving it out keeps
 * the count independent of the day an exchange rate is read on. The realized amount is consequently a classification,
 * not a contribution to the return, which is measured from equity instead.
 * </p>
 */
public class AlgoReplayRoundTrips {

  private static final double FLAT = 1e-8;

  private record Key(Integer idAlgoStrategy, Integer idSecuritycurrency) {
  }

  private final Map<Key, Double> openUnits = new HashMap<>();
  private final Map<Key, Double> proceeds = new HashMap<>();
  private final List<Trade> closed = new ArrayList<>();

  /**
   * Adds one executed fill.
   *
   * @param idAlgoStrategy     strategy the fill belongs to, null for a rebalancing order, which is a lifecycle of its
   *                           own
   * @param idSecuritycurrency the instrument
   * @param type               whether the fill increased or reduced the holding
   * @param units              absolute number of units, always positive
   * @param quotation          price per unit in the currency of the instrument
   * @param cost               transaction cost of the fill in the currency of the instrument, null when it had none; it
   *                           leaves the account either way, so a round trip that only looks profitable gross must not
   *                           be counted as a win
   */
  public void add(Integer idAlgoStrategy, Integer idSecuritycurrency, TransactionType type, double units,
      double quotation, Double cost) {
    add(idAlgoStrategy, idSecuritycurrency, type, units, quotation, cost, null, null);
  }

  /** Taxes reduce both sides; accrued interest is signed gross consideration transferred with the bond. */
  public void add(Integer idAlgoStrategy, Integer idSecuritycurrency, TransactionType type, double units,
      double quotation, Double cost, Double tax, Double accruedInterest) {
    Key key = new Key(idAlgoStrategy, idSecuritycurrency);
    double signedUnits = type == TransactionType.ACCUMULATE ? units : -units;
    openUnits.merge(key, signedUnits, Double::sum);
    double accrued = (accruedInterest == null ? 0 : accruedInterest) * (type == TransactionType.ACCUMULATE ? -1 : 1);
    proceeds.merge(key, -signedUnits * quotation + accrued - (cost == null ? 0 : cost) - (tax == null ? 0 : tax),
        Double::sum);
    if (Math.abs(openUnits.get(key)) < FLAT) {
      closed.add(new Trade(proceeds.remove(key)));
      openUnits.remove(key);
    }
  }

  /**
   * @return the round trips that closed, in the order they closed; a position still open on the end date contributes
   *         none, because what it is worth belongs to the terminal equity rather than to a realized result
   */
  public List<Trade> closedTrades() {
    return List.copyOf(closed);
  }
}
