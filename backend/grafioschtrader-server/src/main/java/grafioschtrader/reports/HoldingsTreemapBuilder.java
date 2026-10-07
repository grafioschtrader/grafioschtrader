package grafioschtrader.reports;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.securityaccount.HoldingsTreemap;
import grafioschtrader.reportviews.securityaccount.HoldingsTreemapExcluded;
import grafioschtrader.reportviews.securityaccount.HoldingsTreemapNode;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.HoldingsTreemapNodeType;

/**
 * Builds the holdings treemap of the report "security asset classes with cash" from its finished groups. Every tile is
 * the value on a hypothetical sale at the report date in main currency ({@code accountValueSecurityMC}):
 * <ul>
 * <li>A standard security becomes one tile below its asset class.</li>
 * <li>Cash accounts are summed per currency, below CURRENCY_CASH for the main currency and CURRENCY_FOREIGN
 * otherwise.</li>
 * <li>An open CFD/Forex position gets no tile. Its unrealized gain/loss is added to the cash of the currency of the cash
 * account it settles into, because closing it would book exactly that amount there.</li>
 * </ul>
 * A value that cannot be drawn - negative, or without price or exchange rate - is returned in the excluded list, so
 * that the tiles plus the exclusions still add up to {@code grandAccountValueSecurityMC}. The margin merge only moves
 * value between branches and never changes that total.
 */
public final class HoldingsTreemapBuilder {

  static final String ROOT_ID = "root";
  private static final String ASSETCLASS_PREFIX = "ac/";
  private static final String SECURITY_PREFIX = "sec/";
  private static final String CASH_PREFIX = "cash/";

  private HoldingsTreemapBuilder() {
  }

  /**
   * Creates the node list and the exclusions of the treemap.
   *
   * @param groups                      the asset class groups of the report, after the main currency values and the
   *                                    grand total were calculated
   * @param mainCurrency                the main currency of the tenant
   * @param precisionMC                 number of fraction digits of the main currency
   * @param grandAccountValueSecurityMC report total, the denominator of every share
   * @return the treemap, never null
   */
  public static HoldingsTreemap build(List<SecurityPositionDynamicGroupSummary<AssetclassType>> groups,
      String mainCurrency, int precisionMC, double grandAccountValueSecurityMC) {
    Collector collector = new Collector(mainCurrency, precisionMC, grandAccountValueSecurityMC);
    groups.forEach(collector::addGroup);
    collector.addCashLeaves();
    return new HoldingsTreemap(collector.createNodes(), collector.excluded);
  }

  /** Accumulates leaves and exclusions while the positions are walked. */
  private static final class Collector {
    private final String mainCurrency;
    private final int precisionMC;
    private final double grandTotal;
    private final Map<AssetclassType, List<HoldingsTreemapNode>> leavesByAssetclass = new TreeMap<>(
        Comparator.nullsLast(Comparator.naturalOrder()));
    private final Map<String, Double> cashByCurrency = new TreeMap<>();
    private final Map<String, Double> marginByCurrency = new HashMap<>();
    private final Map<String, Set<String>> marginNamesByCurrency = new HashMap<>();
    private final List<HoldingsTreemapExcluded> excluded = new ArrayList<>();

    Collector(String mainCurrency, int precisionMC, double grandTotal) {
      this.mainCurrency = mainCurrency;
      this.precisionMC = precisionMC;
      this.grandTotal = grandTotal;
    }

    void addGroup(SecurityPositionDynamicGroupSummary<AssetclassType> group) {
      for (SecurityPositionSummary position : group.securityPositionSummaryList) {
        Security security = position.getSecurity();
        if (security.getIdSecuritycurrency() < 0) {
          addCashPosition(position);
        } else if (security.isMarginInstrument()) {
          addMarginPosition(position);
        } else {
          addSecurityPosition(group.groupField, position);
        }
      }
    }

    private void addCashPosition(SecurityPositionSummary position) {
      Security security = position.getSecurity();
      if (position.priceMissing && position.valueSecurity != 0) {
        excluded.add(new HoldingsTreemapExcluded(security.getName(), round(position.accountValueSecurityMC),
            HoldingsTreemapExcluded.TREEMAP_PRICE_MISSING));
      } else {
        cashByCurrency.merge(security.getCurrency(), position.accountValueSecurityMC, Double::sum);
      }
    }

    /**
     * Moves the unrealized gain/loss of the open positions of a margin instrument to the cash of their settlement
     * currencies. Without a split per settlement currency, the security currency is the best available guess.
     */
    private void addMarginPosition(SecurityPositionSummary position) {
      Security security = position.getSecurity();
      Map<String, Double> byCurrency = position.marginGainLossBySettlementCurrencyMC;
      if (position.priceMissing && position.units != 0) {
        excluded.add(new HoldingsTreemapExcluded(security.getName(), round(position.accountValueSecurityMC),
            HoldingsTreemapExcluded.TREEMAP_PRICE_MISSING));
      } else if (byCurrency == null || byCurrency.isEmpty()) {
        if (round(position.accountValueSecurityMC) != 0) {
          addMarginGainLoss(security.getCurrency(), position.accountValueSecurityMC, security.getName());
        }
      } else {
        byCurrency.forEach((currency, gainLossMC) -> addMarginGainLoss(currency, gainLossMC, security.getName()));
      }
    }

    private void addMarginGainLoss(String currency, double gainLossMC, String securityName) {
      cashByCurrency.merge(currency, gainLossMC, Double::sum);
      marginByCurrency.merge(currency, gainLossMC, Double::sum);
      marginNamesByCurrency.computeIfAbsent(currency, _ -> new TreeSet<>()).add(securityName);
    }

    private void addSecurityPosition(AssetclassType assetclassType, SecurityPositionSummary position) {
      Security security = position.getSecurity();
      double value = round(position.accountValueSecurityMC);
      if (value == 0 && position.units == 0) {
        // Closed position shown because closed positions are included
        return;
      }
      if (position.priceMissing) {
        excluded.add(new HoldingsTreemapExcluded(security.getName(), value,
            HoldingsTreemapExcluded.TREEMAP_PRICE_MISSING));
      } else if (value < 0) {
        excluded.add(new HoldingsTreemapExcluded(security.getName(), value,
            HoldingsTreemapExcluded.TREEMAP_NEGATIVE_VALUE));
      } else if (value > 0) {
        addLeaf(assetclassType,
            new HoldingsTreemapNode(SECURITY_PREFIX + security.getIdSecuritycurrency(),
                assetclassId(assetclassType), HoldingsTreemapNodeType.SECURITY, security.getName(), value, value,
                position.getShareOfTotalPercentage(), security.getIdSecuritycurrency(),
                finiteOrNull(position.transactionGainLossPercentage), null, null));
      }
    }

    /** Turns the summed cash per currency into leaves, after all margin positions were merged into it. */
    void addCashLeaves() {
      cashByCurrency.forEach((currency, cashMC) -> {
        double value = round(cashMC);
        if (value < 0) {
          excluded.add(new HoldingsTreemapExcluded(currency, value, HoldingsTreemapExcluded.TREEMAP_NEGATIVE_VALUE));
        } else if (value > 0) {
          AssetclassType cashClass = currency.equals(mainCurrency) ? AssetclassType.CURRENCY_CASH
              : AssetclassType.CURRENCY_FOREIGN;
          Double marginMC = marginByCurrency.get(currency);
          Set<String> marginNames = marginNamesByCurrency.get(currency);
          addLeaf(cashClass,
              new HoldingsTreemapNode(CASH_PREFIX + cashClass.name() + "/" + currency, assetclassId(cashClass),
                  HoldingsTreemapNodeType.CASH_CURRENCY, currency, value, value, shareOfTotal(value), null, null,
                  marginMC == null ? null : round(marginMC),
                  marginNames == null ? null : List.copyOf(marginNames)));
        }
      });
    }

    private void addLeaf(AssetclassType assetclassType, HoldingsTreemapNode leaf) {
      leavesByAssetclass.computeIfAbsent(assetclassType, _ -> new ArrayList<>()).add(leaf);
    }

    /**
     * Emits the root, then every asset class that has at least one leaf in the order of {@link AssetclassType},
     * each followed by its leaves ordered by descending value. Parents carry a value of 0 because the treemap sums
     * the children itself; their displayed total is the sum of their leaves.
     */
    List<HoldingsTreemapNode> createNodes() {
      List<HoldingsTreemapNode> branches = new ArrayList<>();
      double rootTotal = 0;
      for (Map.Entry<AssetclassType, List<HoldingsTreemapNode>> entry : leavesByAssetclass.entrySet()) {
        List<HoldingsTreemapNode> leaves = entry.getValue();
        leaves.sort(Comparator.comparingDouble(HoldingsTreemapNode::valueMC).reversed());
        double total = round(leaves.stream().mapToDouble(HoldingsTreemapNode::valueMC).sum());
        rootTotal += total;
        AssetclassType assetclassType = entry.getKey();
        branches.add(new HoldingsTreemapNode(assetclassId(assetclassType), ROOT_ID,
            HoldingsTreemapNodeType.ASSETCLASS, assetclassType == null ? null : assetclassType.name(), 0, total,
            shareOfTotal(total), null, null, null, null));
        branches.addAll(leaves);
      }
      rootTotal = round(rootTotal);
      List<HoldingsTreemapNode> nodes = new ArrayList<>();
      nodes.add(new HoldingsTreemapNode(ROOT_ID, null, HoldingsTreemapNodeType.ROOT, null, 0, rootTotal,
          shareOfTotal(rootTotal), null, null, null, null));
      nodes.addAll(branches);
      return nodes;
    }

    private Double shareOfTotal(double valueMC) {
      return grandTotal > 0 ? DataBusinessHelper.roundPercentage(valueMC * 100.0 / grandTotal) : null;
    }

    private double round(double valueMC) {
      return DataHelper.round(valueMC, precisionMC);
    }

    private static String assetclassId(AssetclassType assetclassType) {
      return ASSETCLASS_PREFIX + assetclassType;
    }

    private static Double finiteOrNull(Double value) {
      return value != null && Double.isFinite(value) ? value : null;
    }
  }
}
