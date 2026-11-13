package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.types.CostBasisMethod;

/**
 * The open purchases of one security in one security account, in the order they were bought. A sale consumes them from
 * the head (FIFO) or from the tail (LIFO).
 *
 * <p>
 * The ledger only keeps quantities and costs. It does not decide what an uncovered sale costs: it reports the units
 * no lot covered and leaves the substitute to the caller, because the callers disagree about it.
 * </p>
 */
public final class CostLedger {

  private final Deque<CostLot> lots = new ArrayDeque<>();
  private double units;

  /**
   * Result of a sale against the ledger.
   *
   * @param consumedLots   the consumed parts, as copies the ledger no longer changes, in the order of consumption
   * @param cost           sum of the cost of the consumed parts in security currency
   * @param uncoveredUnits units of the sale that no lot covered, 0 for a covered sale
   */
  public record Reduction(List<CostLot> consumedLots, double cost, double uncoveredUnits) {
  }

  /**
   * Appends a purchase as the newest lot.
   *
   * @param lot the purchase, with its units on the split basis of the report date
   */
  public void add(CostLot lot) {
    lots.addLast(lot);
    units += lot.units;
  }

  /**
   * Consumes lots for a sale. A lot that is used up hands over its whole cost, a partly used lot the proportional
   * share, so no cost remainder is left behind. A lot whose remaining units round to zero counts as used up. When the
   * units of the ledger round to zero, whatever is left goes to this sale as well and the ledger is emptied, so that
   * a closed position cannot come back as the oldest lot of a later purchase.
   *
   * @param unitsToReduce units of the sale on the split basis of the report date
   * @param method        {@link CostBasisMethod#FIFO} or {@link CostBasisMethod#LIFO}
   * @return the consumed parts, their cost and the units that no lot covered
   */
  public Reduction reduce(double unitsToReduce, CostBasisMethod method) {
    final List<CostLot> consumedLots = new ArrayList<>();
    double cost = 0.0;
    double remaining = unitsToReduce;
    while (!lots.isEmpty() && DataBusinessHelper.round(remaining) > 0.0) {
      final CostLot lot = method == CostBasisMethod.LIFO ? lots.peekLast() : lots.peekFirst();
      if (DataBusinessHelper.round(lot.units - remaining) <= 0.0) {
        cost += consume(lot, lot.units, lot.cost, consumedLots);
        remaining -= lot.units;
        remove(lot, method);
      } else {
        final double partCost = lot.cost * remaining / lot.units;
        cost += consume(lot, remaining, partCost, consumedLots);
        lot.units -= remaining;
        lot.cost -= partCost;
        units -= remaining;
        remaining = 0.0;
      }
    }
    if (DataBusinessHelper.round(units) <= 0.0) {
      for (CostLot lot : lots) {
        cost += consume(lot, lot.units, lot.cost, consumedLots);
      }
      lots.clear();
      units = 0.0;
    }
    final double uncoveredUnits = DataBusinessHelper.round(remaining) > 0.0 ? remaining : 0.0;
    return new Reduction(consumedLots, cost, uncoveredUnits);
  }

  private double consume(CostLot lot, double consumedUnits, double consumedCost, List<CostLot> consumedLots) {
    consumedLots
        .add(new CostLot(lot.idTransaction, lot.transactionTime, lot.exchangeRate, consumedUnits, consumedCost));
    return consumedCost;
  }

  private void remove(CostLot lot, CostBasisMethod method) {
    if (method == CostBasisMethod.LIFO) {
      lots.removeLast();
    } else {
      lots.removeFirst();
    }
    units -= lot.units;
  }

  /** Units of all open lots. */
  public double getUnits() {
    return units;
  }

  /** Cost of all open lots in security currency. */
  public double getCost() {
    return lots.stream().mapToDouble(lot -> lot.cost).sum();
  }

  /** The open lots from the oldest to the newest, as a copy of the list but not of the lots. */
  public List<CostLot> getLots() {
    return new ArrayList<>(lots);
  }

  public boolean isEmpty() {
    return lots.isEmpty();
  }
}
