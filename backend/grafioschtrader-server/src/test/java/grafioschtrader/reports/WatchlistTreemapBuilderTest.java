package grafioschtrader.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.types.Language;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.reports.WatchlistTreemapBuilder.Amount;
import grafioschtrader.reports.WatchlistTreemapBuilder.Input;
import grafioschtrader.reportviews.securitycurrency.WatchlistTreemap;
import grafioschtrader.reportviews.securitycurrency.WatchlistTreemapExcluded;
import grafioschtrader.reportviews.securitycurrency.WatchlistTreemapNode;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Checks the market map against hand-calculated areas and group changes, and the rule that decides whether a price is
 * still current.
 */
class WatchlistTreemapBuilderTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 14, 0);
  private static final Assetclass SWISS = assetclass(1, "Aktien Schweiz", "Equities Switzerland");
  private static final Assetclass USA = assetclass(2, "Aktien USA", "Equities USA");

  private final Map<Integer, Double> shares = new HashMap<>();
  private final Map<Integer, Amount> marketCaps = new HashMap<>();
  private final Map<Integer, LocalDateTime> closes = new HashMap<>();
  private final Map<String, Double> rates = new HashMap<>(Map.of("USD", 0.8, "EUR", 0.9));

  @Test
  @DisplayName("Area in main currency from shares or stated capitalisation, group change weighted by area")
  void areasAndWeightedChanges() {
    Security inMain = stock(1, "CH1", "CHF", SWISS, 10, 1.0, NOW.minusMinutes(5));
    Security foreign = stock(2, "US2", "USD", SWISS, 50, -2.0, NOW.minusMinutes(5));
    Security fallback = stock(3, "US3", "USD", USA, 70, 3.0, NOW.minusMinutes(5));
    shares.put(1, 1000.0);
    shares.put(2, 100.0);
    marketCaps.put(3, new Amount(2000, "EUR"));

    WatchlistTreemap treemap = build(inMain, foreign, fallback);

    assertThat(treemap.excluded()).isEmpty();
    assertThat(treemap.nodes()).extracting(WatchlistTreemapNode::id).containsExactly("root", "grp/1", "sec/1", "sec/2",
        "grp/2", "sec/3");
    // 1000 x 10 = 10000 CHF; 100 x 50 x 0.8 = 4000 CHF; 2000 EUR x 0.9 = 1800 CHF, not the USD price of the security.
    assertThat(node(treemap, "sec/1").valueMC()).isCloseTo(10000, within(0.001));
    assertThat(node(treemap, "sec/2").valueMC()).isCloseTo(4000, within(0.001));
    assertThat(node(treemap, "sec/3").valueMC()).isCloseTo(1800, within(0.001));
    // (10000 x 1 + 4000 x -2) / 14000 = 0.1429
    assertThat(node(treemap, "grp/1").changePercentage()).isCloseTo(0.1429, within(0.01));
    assertThat(node(treemap, "grp/1").totalValueMC()).isCloseTo(14000, within(0.001));
    assertThat(node(treemap, "grp/1").label()).isEqualTo("Aktien Schweiz");
    // (10000 x 1 + 4000 x -2 + 1800 x 3) / 15800 = 0.4684
    assertThat(node(treemap, "root").changePercentage()).isCloseTo(0.4684, within(0.01));
  }

  @Test
  @DisplayName("A company listed twice gets one tile, from the listing with the newest price")
  void oneTilePerIsin() {
    Security older = stock(1, "CH1", "CHF", SWISS, 10, 1.0, NOW.minusMinutes(30));
    Security newer = stock(2, "CH1", "CHF", SWISS, 10, 2.0, NOW.minusMinutes(5));
    shares.put(1, 1000.0);
    shares.put(2, 1000.0);

    WatchlistTreemap treemap = build(older, newer);

    assertThat(treemap.nodes()).extracting(WatchlistTreemapNode::id).containsExactly("root", "grp/1", "sec/2");
    assertThat(treemap.excluded()).isEmpty();
  }

  @Test
  @DisplayName("Each stock that cannot be drawn is reported with the first reason that applies")
  void exclusionReasons() {
    Security noChange = stock(1, "A", "CHF", SWISS, 10, null, NOW.minusDays(5));
    Security stale = stock(2, "B", "CHF", SWISS, 10, 1.0, NOW.minusDays(5));
    Security noCap = stock(3, "C", "CHF", SWISS, 10, 1.0, NOW.minusMinutes(5));
    Security noRate = stock(4, "D", "JPY", SWISS, 10, 1.0, NOW.minusMinutes(5));
    shares.put(4, 1000.0);

    WatchlistTreemap treemap = build(noChange, stale, noCap, noRate);

    assertThat(treemap.nodes()).isEmpty();
    assertThat(treemap.excluded()).extracting(WatchlistTreemapExcluded::label, WatchlistTreemapExcluded::reasonKey)
        .containsExactlyInAnyOrder(tuple("name1", WatchlistTreemapBuilder.REASON_CHANGE_MISSING),
            tuple("name2", WatchlistTreemapBuilder.REASON_CHANGE_STALE),
            tuple("name3", WatchlistTreemapBuilder.REASON_MARKET_CAP_MISSING),
            tuple("name4", WatchlistTreemapBuilder.REASON_PRICE_MISSING));
  }

  @Test
  @DisplayName("Exchange in session: one hour behind the newest price of the watchlist is the limit")
  void inSessionTheNewestPriceIsTheReference() {
    closes.put(2, NOW.plusHours(3));
    closes.put(3, NOW.plusHours(3));
    assertThat(drawnIds(priced(1, NOW), priced(2, NOW.minusMinutes(60)), priced(3, NOW.minusMinutes(61))))
        .containsExactlyInAnyOrder("sec/1", "sec/2");
  }

  @Test
  @DisplayName("Closed exchange: a price from the close stays, although other connectors date theirs later")
  void afterCloseTheCloseIsTheReference() {
    LocalDateTime close = NOW.minusHours(4);
    closes.put(2, close);
    closes.put(3, close);
    closes.put(4, close);
    assertThat(drawnIds(priced(1, NOW), priced(2, close.plusMinutes(5)), priced(3, close.minusMinutes(60)),
        priced(4, close.minusMinutes(61)))).containsExactlyInAnyOrder("sec/1", "sec/2", "sec/3");
  }

  @Test
  @DisplayName("Without a known close the newest price of the watchlist is the reference")
  void unknownCloseFallsBackToNewestPrice() {
    assertThat(drawnIds(priced(1, NOW), priced(2, NOW.minusMinutes(61)))).containsExactly("sec/1");
  }

  @Test
  @DisplayName("A price dated in the future does not become the reference of the others")
  void futureTimestampDoesNotShiftTheReference() {
    assertThat(drawnIds(priced(1, NOW.plusHours(2)), priced(2, NOW.minusMinutes(30))))
        .containsExactlyInAnyOrder("sec/1", "sec/2");
  }

  /** A stock with a distinct ISIN that has everything needed for a tile. */
  private Security priced(int id, LocalDateTime timestamp) {
    shares.put(id, 1000.0);
    return stock(id, "ISIN" + id, "CHF", SWISS, 10, 1.0, timestamp);
  }

  private List<String> drawnIds(Security... stocks) {
    return build(stocks).nodes().stream().map(WatchlistTreemapNode::id).filter(id -> id.startsWith("sec/")).toList();
  }

  private WatchlistTreemap build(Security... stocks) {
    return WatchlistTreemapBuilder.build(
        new Input(List.of(stocks), shares, marketCaps, rates, closes, "CHF", Language.GERMAN, 0, NOW));
  }

  private static WatchlistTreemapNode node(WatchlistTreemap treemap, String id) {
    return treemap.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
  }

  private static Security stock(int id, String isin, String currency, Assetclass assetclass, double price,
      Double change, LocalDateTime timestamp) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setName("name" + id);
    security.setIsin(isin);
    security.setCurrency(currency);
    security.setAssetClass(assetclass);
    security.setActiveToDate(LocalDate.of(2099, 12, 31));
    security.setStockexchange(new Stockexchange("TEST", "Test", "CH", false, false, LocalTime.of(9, 0),
        LocalTime.of(17, 30), "Europe/Zurich", null));
    security.setSLast(price);
    security.setSChangePercentage(change);
    security.setSTimestamp(timestamp);
    return security;
  }

  private static Assetclass assetclass(int id, String german, String english) {
    Assetclass assetclass = new Assetclass(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        german, english);
    assetclass.setIdAssetClass(id);
    return assetclass;
  }
}
