package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.securityaccount.HoldingsTreemap;
import grafioschtrader.reportviews.securityaccount.HoldingsTreemapExcluded;
import grafioschtrader.reportviews.securityaccount.HoldingsTreemapNode;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.HoldingsTreemapNodeType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Holdings treemap of the report "security asset classes with cash", built from hand-made positions without a
 * database. Every case also checks the structural invariants: all tiles plus all exclusions add up to the report total,
 * every parent id resolves, and no asset class node is empty.
 */
class HoldingsTreemapBuilderTest {

  private static final String MAIN_CURRENCY = "CHF";

  private final Map<AssetclassType, List<SecurityPositionSummary>> positionsByClass = new LinkedHashMap<>();
  private double grandTotal;

  @Test
  @DisplayName("A stock and main currency cash become one leaf each")
  void stockAndCash() {
    add(AssetclassType.EQUITIES, stock(1, 10_000));
    add(AssetclassType.CURRENCY_CASH, cash(-1, MAIN_CURRENCY, 5_000));

    HoldingsTreemap treemap = build();

    assertEquals(10_000, node(treemap, "sec/1").valueMC());
    assertEquals(5_000, node(treemap, "cash/CURRENCY_CASH/CHF").valueMC());
    assertEquals("ac/CURRENCY_CASH", node(treemap, "cash/CURRENCY_CASH/CHF").parentId());
    assertEquals(15_000, node(treemap, HoldingsTreemapBuilder.ROOT_ID).totalValueMC());
    assertEquals(0, node(treemap, "ac/EQUITIES").valueMC());
    assertEquals(10_000, node(treemap, "ac/EQUITIES").totalValueMC());
    assertTrue(treemap.excluded().isEmpty());
  }

  @Test
  @DisplayName("Shares of the tiles are taken from the report total and match the position share")
  void sharesOfTotal() {
    SecurityPositionSummary stock = stock(1, 7_500);
    add(AssetclassType.EQUITIES, stock);
    add(AssetclassType.CURRENCY_CASH, cash(-1, MAIN_CURRENCY, 2_500));

    HoldingsTreemap treemap = build();

    assertEquals(75.0, node(treemap, "sec/1").shareOfTotalPercentage());
    assertEquals(stock.getShareOfTotalPercentage(), node(treemap, "sec/1").shareOfTotalPercentage());
    assertEquals(25.0, node(treemap, "cash/CURRENCY_CASH/CHF").shareOfTotalPercentage());
    assertEquals(75.0, node(treemap, "ac/EQUITIES").shareOfTotalPercentage());
    assertEquals(100.0, node(treemap, HoldingsTreemapBuilder.ROOT_ID).shareOfTotalPercentage());
  }

  @Test
  @DisplayName("Forex gain/loss moves to the cash of its settlement currencies, without a security tile")
  void marginMovesToSettlementCash() {
    add(AssetclassType.EQUITIES, stock(1, 10_000));
    add(AssetclassType.CURRENCY_CASH, cash(-1, MAIN_CURRENCY, 5_000));
    add(AssetclassType.CURRENCY_FOREIGN, cash(-2, "USD", 1_000));
    add(AssetclassType.CURRENCY_PAIR, forex(2, "EUR/USD", "USD", Map.of(MAIN_CURRENCY, -200.0, "USD", 50.0)));

    HoldingsTreemap treemap = build();

    assertFalse(findNode(treemap, "sec/2").isPresent());
    assertFalse(findNode(treemap, "ac/CURRENCY_PAIR").isPresent());
    HoldingsTreemapNode chf = node(treemap, "cash/CURRENCY_CASH/CHF");
    assertEquals(4_800, chf.valueMC());
    assertEquals(-200.0, chf.marginGainLossMC());
    assertEquals(List.of("EUR/USD"), chf.marginPositionNames());
    HoldingsTreemapNode usd = node(treemap, "cash/CURRENCY_FOREIGN/USD");
    assertEquals(1_050, usd.valueMC());
    assertEquals(50.0, usd.marginGainLossMC());
  }

  @Test
  @DisplayName("A CHF/JPY position priced in JPY books its gain/loss to the CHF cash it settles into")
  void marginSettlementCurrencyIsNotSecurityCurrency() {
    add(AssetclassType.CURRENCY_CASH, cash(-1, MAIN_CURRENCY, 1_000));
    add(AssetclassType.CURRENCY_PAIR, forex(2, "CHF/JPY", "JPY", Map.of(MAIN_CURRENCY, 120.0)));

    HoldingsTreemap treemap = build();

    assertEquals(1_120, node(treemap, "cash/CURRENCY_CASH/CHF").valueMC());
    assertFalse(findNode(treemap, "cash/CURRENCY_FOREIGN/JPY").isPresent());
  }

  @Test
  @DisplayName("Overdrawn foreign cash is not drawn and listed as negative value")
  void negativeCashIsExcluded() {
    add(AssetclassType.EQUITIES, stock(1, 10_000));
    add(AssetclassType.CURRENCY_FOREIGN, cash(-2, "USD", -300));

    HoldingsTreemap treemap = build();

    assertFalse(findNode(treemap, "cash/CURRENCY_FOREIGN/USD").isPresent());
    assertFalse(findNode(treemap, "ac/CURRENCY_FOREIGN").isPresent());
    assertEquals(List.of(new HoldingsTreemapExcluded("USD", -300, HoldingsTreemapExcluded.TREEMAP_NEGATIVE_VALUE)),
        treemap.excluded());
  }

  @Test
  @DisplayName("A position without price is listed as such instead of being drawn")
  void priceMissingIsExcluded() {
    add(AssetclassType.EQUITIES, stock(1, 10_000));
    SecurityPositionSummary missing = stock(2, 0);
    missing.priceMissing = true;
    add(AssetclassType.EQUITIES, missing);

    HoldingsTreemap treemap = build();

    assertFalse(findNode(treemap, "sec/2").isPresent());
    assertEquals(HoldingsTreemapExcluded.TREEMAP_PRICE_MISSING, treemap.excluded().getFirst().reasonKey());
  }

  @Test
  @DisplayName("A closed position with value 0 is neither drawn nor excluded")
  void closedPositionIsSkipped() {
    add(AssetclassType.EQUITIES, stock(1, 10_000));
    SecurityPositionSummary closed = stock(2, 0);
    closed.units = 0;
    add(AssetclassType.FIXED_INCOME, closed);

    HoldingsTreemap treemap = build();

    assertFalse(findNode(treemap, "sec/2").isPresent());
    assertFalse(findNode(treemap, "ac/FIXED_INCOME").isPresent());
    assertTrue(treemap.excluded().isEmpty());
  }

  @Test
  @DisplayName("A report total that is not positive leaves every share empty")
  void nonPositiveTotalLeavesSharesNull() {
    add(AssetclassType.EQUITIES, stock(1, 1_000));
    add(AssetclassType.CURRENCY_CASH, cash(-1, MAIN_CURRENCY, -3_000));

    HoldingsTreemap treemap = build();

    assertNull(node(treemap, "sec/1").shareOfTotalPercentage());
    assertNull(node(treemap, HoldingsTreemapBuilder.ROOT_ID).shareOfTotalPercentage());
  }

  @Test
  @DisplayName("Asset classes follow the order of AssetclassType, their children descend by value")
  void ordering() {
    add(AssetclassType.CURRENCY_CASH, cash(-1, MAIN_CURRENCY, 500));
    add(AssetclassType.FIXED_INCOME, stock(3, 2_000));
    add(AssetclassType.EQUITIES, stock(1, 1_000));
    add(AssetclassType.EQUITIES, stock(2, 3_000));

    HoldingsTreemap treemap = build();

    List<String> ids = treemap.nodes().stream().map(HoldingsTreemapNode::id).toList();
    assertEquals(List.of("root", "ac/EQUITIES", "sec/2", "sec/1", "ac/FIXED_INCOME", "sec/3", "ac/CURRENCY_CASH",
        "cash/CURRENCY_CASH/CHF"), ids);
  }

  private void add(AssetclassType assetclassType, SecurityPositionSummary position) {
    positionsByClass.computeIfAbsent(assetclassType, _ -> new ArrayList<>()).add(position);
  }

  /** Builds the groups and the grand total like the report does, then the treemap, and checks the invariants. */
  private HoldingsTreemap build() {
    var grand = new SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<AssetclassType>>(
        MAIN_CURRENCY, 2);
    List<SecurityPositionDynamicGroupSummary<AssetclassType>> groups = new ArrayList<>();
    positionsByClass.forEach((assetclassType, positions) -> {
      var group = new SecurityPositionDynamicGroupSummary<AssetclassType>(assetclassType);
      positions.forEach(group::addToGroupSummaryAndCalcGroupTotals);
      grand.calcGrandTotal(group);
      groups.add(group);
    });
    grand.roundGrandTotals();
    grand.calcShareOfTotalPercentages();
    grandTotal = grand.grandAccountValueSecurityMC;
    HoldingsTreemap treemap = HoldingsTreemapBuilder.build(groups, MAIN_CURRENCY, 2, grandTotal);
    assertInvariants(treemap);
    return treemap;
  }

  private void assertInvariants(HoldingsTreemap treemap) {
    double leaves = treemap.nodes().stream().mapToDouble(HoldingsTreemapNode::valueMC).sum();
    double excluded = treemap.excluded().stream().mapToDouble(HoldingsTreemapExcluded::valueMC).sum();
    assertEquals(grandTotal, leaves + excluded, 0.005 * Math.max(1, treemap.nodes().size()));

    Set<String> ids = treemap.nodes().stream().map(HoldingsTreemapNode::id).collect(Collectors.toSet());
    treemap.nodes().stream().filter(n -> n.parentId() != null)
        .forEach(n -> assertTrue(ids.contains(n.parentId()), "Unresolved parent of " + n.id()));
    treemap.nodes().stream().filter(n -> n.nodeType() == HoldingsTreemapNodeType.ASSETCLASS)
        .forEach(ac -> assertTrue(treemap.nodes().stream().anyMatch(n -> ac.id().equals(n.parentId())),
            "Empty asset class " + ac.id()));
  }

  private HoldingsTreemapNode node(HoldingsTreemap treemap, String id) {
    return findNode(treemap, id).orElseThrow(() -> new AssertionError("Missing node " + id));
  }

  private Optional<HoldingsTreemapNode> findNode(HoldingsTreemap treemap, String id) {
    return treemap.nodes().stream().filter(n -> n.id().equals(id)).findFirst();
  }

  private SecurityPositionSummary stock(int id, double valueMC) {
    SecurityPositionSummary position = position(security(id, "Stock " + id, MAIN_CURRENCY,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT), valueMC);
    position.units = 10;
    position.transactionGainLossPercentage = 5.0;
    return position;
  }

  private SecurityPositionSummary cash(int id, String currency, double valueMC) {
    SecurityPositionSummary position = position(security(id, "Cash " + currency, currency, null), valueMC);
    position.valueSecurity = valueMC;
    return position;
  }

  private SecurityPositionSummary forex(int id, String name, String currency, Map<String, Double> gainLossMC) {
    SecurityPositionSummary position = position(security(id, name, currency, SpecialInvestmentInstruments.FOREX),
        gainLossMC.values().stream().mapToDouble(Double::doubleValue).sum());
    position.units = 1_000;
    position.marginGainLossBySettlementCurrencyMC = gainLossMC;
    return position;
  }

  private Security security(int id, String name, String currency, SpecialInvestmentInstruments instrument) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setName(name);
    security.setCurrency(currency);
    Assetclass assetclass = new Assetclass();
    if (instrument != null) {
      assetclass.setSpecialInvestmentInstrument(instrument);
    }
    security.setAssetClass(assetclass);
    return security;
  }

  private SecurityPositionSummary position(Security security, double accountValueMC) {
    var position = new SecurityPositionSummary(MAIN_CURRENCY, security, Map.of(MAIN_CURRENCY, 2));
    position.accountValueSecurityMC = accountValueMC;
    return position;
  }
}
