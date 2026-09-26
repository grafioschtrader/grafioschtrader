package grafioschtrader.service;

import java.time.LocalDate;
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
 * A position the environment already held on its opening day is a lifecycle that the replay continues, not one it
 * never sees. It is entered with {@link #open} at its value on the opening day, so that selling or redeeming it closes
 * a round trip like any other and its result is what it earned during the run. Without that, every sale of an opening
 * position would leave its lifecycle short by the opening units and no such round trip could ever close.
 * </p>
 *
 * <p>
 * Whether a lifecycle is flat is decided in one unit basis for the whole run, supplied by {@link #useUnitBasis}, so a
 * split between two fills does not leave a closed position with a remainder. The amounts stay in the units and prices
 * that were actually booked, because a split changes neither what was paid nor what was received.
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

  /** Remaining units below this are flat; the booked units are rounded, so a split basis can leave dust. */
  private static final double FLAT = 1e-6;

  /** Converts units held on a day into the basis all lifecycles of the run are compared in. */
  @FunctionalInterface
  public interface UnitBasis {
    /**
     * @param idSecuritycurrency the instrument
     * @param date               the day the units belong to
     * @return the factor that turns units of that day into units of the common basis
     */
    double factor(Integer idSecuritycurrency, LocalDate date);
  }

  private record Key(Integer idAlgoStrategy, Integer idSecuritycurrency) {
  }

  private final Map<Key, Double> openUnits = new HashMap<>();
  private final Map<Key, Double> proceeds = new HashMap<>();
  private final List<Trade> closed = new ArrayList<>();
  private UnitBasis unitBasis = (_, _) -> 1.0;

  /**
   * Sets the unit basis in which lifecycles are checked for being flat. Fills added without a date are not converted.
   *
   * @param unitBasis the conversion, typically to the split basis of the last day of the run
   */
  public void useUnitBasis(UnitBasis unitBasis) {
    this.unitBasis = unitBasis;
  }

  /**
   * Enters a position held on the opening day as an open lifecycle, bought at its value on that day.
   *
   * @param idAlgoStrategy     strategy the position belongs to, null when it is no strategy's
   * @param idSecuritycurrency the instrument
   * @param units              units held on {@code date}; nothing is entered unless positive
   * @param price              closing price of that day in the currency of the instrument
   * @param date               the opening day
   */
  public void open(Integer idAlgoStrategy, Integer idSecuritycurrency, double units, double price, LocalDate date) {
    if (units > FLAT) {
      add(idAlgoStrategy, idSecuritycurrency, TransactionType.ACCUMULATE, units, price, null, null, null, date);
    }
  }

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
    add(idAlgoStrategy, idSecuritycurrency, type, units, quotation, cost, null, null, null);
  }

  /** Taxes reduce both sides; accrued interest is signed gross consideration transferred with the bond. */
  public void add(Integer idAlgoStrategy, Integer idSecuritycurrency, TransactionType type, double units,
      double quotation, Double cost, Double tax, Double accruedInterest) {
    add(idAlgoStrategy, idSecuritycurrency, type, units, quotation, cost, tax, accruedInterest, null);
  }

  /**
   * Adds one executed fill booked on the given day.
   *
   * @param date the fill date, whose units are converted with the unit basis; null leaves them as they are
   */
  public void add(Integer idAlgoStrategy, Integer idSecuritycurrency, TransactionType type, double units,
      double quotation, Double cost, Double tax, Double accruedInterest, LocalDate date) {
    Key key = new Key(idAlgoStrategy, idSecuritycurrency);
    double signedUnits = type == TransactionType.ACCUMULATE ? units : -units;
    double basisFactor = date == null ? 1 : unitBasis.factor(idSecuritycurrency, date);
    openUnits.merge(key, signedUnits * basisFactor, Double::sum);
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
