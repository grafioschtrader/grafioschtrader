package grafioschtrader.instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.securityaccount.CostLedger;
import grafioschtrader.reportviews.securityaccount.CostLot;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.CostBasisMethod;

/** Determines the cost removed from the parent and the lots received by the child in a calculation scope. */
final class SpinOffAllocator {
  record Allocation(double allocatedCost, double childCost, List<CostLot> parentLotsAfter, List<CostLot> childLots,
      double childInvestedMC, boolean incomplete) {
  }

  private SpinOffAllocator() {
  }

  static Allocation allocate(SecurityPositionSummary parent, Transaction child, CostBasisMethod method,
      double parentUnits, double childUnits, double q, double fx, Double parentRate, int parentPrecision,
      int childPrecision) {
    if (method == CostBasisMethod.AVERAGE_COST) {
      double poolUnits = parent.averageUnits();
      double allocated = poolUnits > 0
          ? DataHelper.round(q * parentUnits / poolUnits * parent.averageCost(), parentPrecision)
          : 0;
      return new Allocation(allocated, DataHelper.round(allocated * fx, childPrecision), List.of(), List.of(),
          parentRate == null ? 0 : allocated * parentRate, poolUnits <= 0);
    }
    CostLedger ledger = parent.getCostLedger(child.getIdSecurityaccount());
    List<CostLot> lots = ledger == null ? List.of() : ledger.getLots();
    double knownUnits = ledger == null ? 0 : ledger.getUnits();
    double[] lotCosts = lots.stream().mapToDouble(lot -> Math.max(lot.cost, 0)).toArray();
    double[] movedParts = SpinOffCostDistribution.distribute(
        DataHelper.round(q * (ledger == null ? 0 : ledger.getCost()), parentPrecision), lotCosts, lotCosts,
        parentPrecision);
    double allocated = SpinOffCostDistribution.sum(movedParts, parentPrecision);
    double childCost = DataHelper.round(allocated * fx, childPrecision);
    double[] convertedParts = SpinOffCostDistribution.distribute(childCost, movedParts, null, childPrecision);

    List<CostLot> parentLots = new ArrayList<>();
    List<CostLot> childLots = new ArrayList<>();
    double inheritedMain = 0;
    boolean incomplete = false;
    for (int i = 0; i < lots.size(); i++) {
      CostLot lot = lots.get(i);
      double moved = movedParts[i];
      double converted = convertedParts[i];
      parentLots.add(lot.withAmount(lot.units, lot.cost - moved));
      Double rate = Objects.equals(parent.mainCurrency, parent.getSecurity().getCurrency()) ? Double.valueOf(1)
          : lot.exchangeRate;
      double movedMain = rate == null ? 0 : moved * rate;
      Double inheritedRate = rate == null || converted == 0
          || child.getSecurity().getCurrency().equals(parent.mainCurrency) ? null : movedMain / converted;
      inheritedMain += movedMain;
      incomplete |= lot.incomplete;
      Integer origin = lot.originIdTransaction == null ? lot.idTransaction : lot.originIdTransaction;
      childLots.add(new CostLot(child.getId(), origin, lot.transactionTime, inheritedRate,
          lot.units / parentUnits * childUnits, converted, lot.incomplete));
    }
    double uncovered = childUnits - childLots.stream().mapToDouble(lot -> lot.units).sum();
    if (DataBusinessHelper.round(parentUnits - knownUnits) > 0 && DataBusinessHelper.round(uncovered) > 0) {
      childLots.add(new CostLot(child.getId(), null, child.getTransactionTime(), null, uncovered, 0, true));
      incomplete = true;
    }
    return new Allocation(allocated, childCost, parentLots, childLots, inheritedMain, incomplete);
  }
}
