package grafioschtrader.tax.germany;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import grafioschtrader.reportviews.germantax.GermanTaxWarning;

/**
 * A living part of a purchase in the German tax calculation, with the tax state of its units in euro. The state follows
 * the lot through sales, security account transfers and ISIN changes: a split divides units, cost and applied advance
 * lump sums in the ratio of the units, and the parts add up to the original again.
 *
 * <p>
 * A lot is incomplete when its cost in euro or one of its advance lump sums is not known completely. It stays
 * incomplete in every part and every move, and a sale that consumes it has no gain.
 * </p>
 */
final class GermanTaxLot {

  /** The purchase the lot stems from, it stays across moves. */
  final Integer idTransaction;
  /** The time of the purchase, which decides the FIFO order and the month of the advance lump sum. */
  final LocalDateTime acquisitionTime;
  /** Units on the split basis of today. */
  double units;
  /** Remaining cost in euro. */
  double costEur;
  /** Advance lump sums applied to the units so far in euro. */
  double preliminaryEur;
  boolean incomplete;
  /** The gaps that made the lot incomplete. Parts of a lot share them. */
  final List<GermanTaxWarning> reasons;

  GermanTaxLot(Integer idTransaction, LocalDateTime acquisitionTime, double units) {
    this(idTransaction, acquisitionTime, units, new ArrayList<>());
  }

  private GermanTaxLot(Integer idTransaction, LocalDateTime acquisitionTime, double units,
      List<GermanTaxWarning> reasons) {
    this.idTransaction = idTransaction;
    this.acquisitionTime = acquisitionTime;
    this.units = units;
    this.reasons = reasons;
  }

  /** Marks the lot as incomplete because of a gap. */
  void markIncomplete(GermanTaxWarning reason) {
    incomplete = true;
    if (!reasons.contains(reason)) {
      reasons.add(reason);
    }
  }

  /**
   * Derives the lot of the new shares of a spin-off from this parent lot: the same purchase and purchase time, so FIFO
   * order and the transition rules of the purchase continue, and the gaps of this lot. The parent lot keeps its units;
   * the caller lowers its cost and advance lump sums by what it hands over.
   *
   * @param childUnits       units of the new shares on the split basis of today
   * @param childCostEur     the euro cost that passes to the new shares
   * @param childPreliminary the applied advance lump sums that pass to the new shares
   * @return the new lot, with its own list of gaps
   */
  GermanTaxLot deriveSpinOff(double childUnits, double childCostEur, double childPreliminary) {
    final GermanTaxLot child = new GermanTaxLot(idTransaction, acquisitionTime, childUnits,
        new ArrayList<>(reasons));
    child.incomplete = incomplete;
    child.costEur = childCostEur;
    child.preliminaryEur = childPreliminary;
    return child;
  }

  /**
   * Takes a part of the lot. A part that is as large as the lot is the lot itself and leaves it empty.
   *
   * @param takeUnits the units of the part, not more than the lot holds
   * @return the part with its share of cost and applied advance lump sums
   */
  GermanTaxLot split(double takeUnits) {
    final double ratio = takeUnits >= units ? 1.0 : takeUnits / units;
    final GermanTaxLot part = new GermanTaxLot(idTransaction, acquisitionTime, ratio == 1.0 ? units : takeUnits,
        reasons);
    part.incomplete = incomplete;
    part.costEur = costEur * ratio;
    part.preliminaryEur = preliminaryEur * ratio;
    units -= part.units;
    costEur -= part.costEur;
    preliminaryEur -= part.preliminaryEur;
    return part;
  }
}
