package grafioschtrader.reports;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import grafiosch.types.Language;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.securitycurrency.WatchlistTreemap;
import grafioschtrader.reportviews.securitycurrency.WatchlistTreemapExcluded;
import grafioschtrader.reportviews.securitycurrency.WatchlistTreemapNode;
import grafioschtrader.types.WatchlistTreemapNodeType;

/**
 * Builds the market map of a watchlist from its loaded stocks. It has no database access, so every rule can be checked
 * with plain objects.
 * <ul>
 * <li>A company listed several times in the watchlist gets one tile: its listing with the newest price.</li>
 * <li>A tile is dropped when its price is more than an hour older than its reference time, the earlier of the newest
 * price of the watchlist and the close of the newest opened session of its exchange. During a session that is the
 * newest price; on a closed market a price from the close stays in, although other connectors date it later.</li>
 * <li>The area is the share count times the price, otherwise the market capitalisation stated by the data source,
 * converted into the main currency of the tenant. The colour stays the change in the currency of the listing.</li>
 * </ul>
 * A stock that cannot be drawn is returned in the excluded list with its reason.
 */
public final class WatchlistTreemapBuilder {

  static final String REASON_CHANGE_MISSING = "TREEMAP_CHANGE_MISSING";
  static final String REASON_CHANGE_STALE = "TREEMAP_CHANGE_STALE";
  static final String REASON_MARKET_CAP_MISSING = "TREEMAP_MARKET_CAP_MISSING";
  static final String REASON_PRICE_MISSING = "TREEMAP_PRICE_MISSING";

  private static final long MAX_MINUTES_BEHIND_REFERENCE = 60;
  private static final String ROOT_ID = "root";
  private static final String GROUP_PREFIX = "grp/";
  private static final String SECURITY_PREFIX = "sec/";

  /** Newest price first; on equal times the lowest id, so that the choice is stable between two calls. */
  private static final Comparator<Security> NEWEST_LISTING_FIRST = Comparator
      .comparing(Security::getSTimestamp, Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(Security::getIdSecuritycurrency);

  private WatchlistTreemapBuilder() {
  }

  /** A market capitalisation in full units of the stated currency. */
  public record Amount(double value, String currency) {
  }

  /**
   * Everything the market map is calculated from.
   *
   * @param stocks             the active direct stocks of the watchlist, with current price and change
   * @param shares             selected share count per security
   * @param marketCaps         market capitalisation stated by the data source per security
   * @param ratesToMain        rate into the main currency per foreign currency; a currency without rate is absent
   * @param closes             close of the newest opened session as UTC time per security; absent when unknown
   * @param mainCurrency       main currency of the tenant
   * @param language           language of the labels of the groups
   * @param notApplicableCount instruments of the watchlist that are no active direct stocks
   * @param now                the current time in UTC
   */
  public record Input(List<Security> stocks, Map<Integer, Double> shares, Map<Integer, Amount> marketCaps,
      Map<String, Double> ratesToMain, Map<Integer, LocalDateTime> closes, String mainCurrency, Language language,
      int notApplicableCount, LocalDateTime now) {
  }

  private record Tile(Security security, double capMC) {
  }

  /**
   * Creates the nodes and the exclusions of the market map.
   *
   * @param input the loaded values
   * @return the market map, never null; without nodes when no stock can be drawn
   */
  public static WatchlistTreemap build(Input input) {
    List<WatchlistTreemapExcluded> excluded = new ArrayList<>();
    List<Tile> tiles = new ArrayList<>();
    LocalDateTime newest = newestPrice(input);
    for (Security security : oneListingPerIsin(input.stocks())) {
      String reason = reasonWithoutCapitalisation(security, input, newest);
      Tile tile = reason == null ? createTile(security, input) : null;
      if (tile != null) {
        tiles.add(tile);
      } else {
        excluded.add(new WatchlistTreemapExcluded(security.getName(),
            reason != null ? reason : missingValueReason(security, input)));
      }
    }
    return new WatchlistTreemap(input.mainCurrency(), input.notApplicableCount(), createNodes(tiles, input), excluded);
  }

  /** The newest price time of the stocks, ignoring a time in the future so that it cannot shift the reference. */
  private static LocalDateTime newestPrice(Input input) {
    return input.stocks().stream().map(Security::getSTimestamp).filter(Objects::nonNull)
        .filter(timestamp -> !timestamp.isAfter(input.now())).max(Comparator.naturalOrder()).orElse(input.now());
  }

  private static List<Security> oneListingPerIsin(List<Security> stocks) {
    Set<String> seenIsin = new HashSet<>();
    return stocks.stream().sorted(NEWEST_LISTING_FIRST)
        .filter(
            security -> security.getIsin() == null || security.getIsin().isBlank() || seenIsin.add(security.getIsin()))
        .toList();
  }

  private static String reasonWithoutCapitalisation(Security security, Input input, LocalDateTime newest) {
    if (security.getSChangePercentage() == null) {
      return REASON_CHANGE_MISSING;
    }
    return isFresh(security, input, newest) ? null : REASON_CHANGE_STALE;
  }

  private static boolean isFresh(Security security, Input input, LocalDateTime newest) {
    if (security.getSTimestamp() == null) {
      return false;
    }
    LocalDateTime close = input.closes().get(security.getIdSecuritycurrency());
    LocalDateTime reference = close != null && close.isBefore(newest) ? close : newest;
    return !security.getSTimestamp().isBefore(reference.minusMinutes(MAX_MINUTES_BEHIND_REFERENCE));
  }

  private static Amount marketCap(Security security, Input input) {
    Double shares = input.shares().get(security.getIdSecuritycurrency());
    if (shares != null && security.getSLast() != null && security.getSLast() > 0) {
      return new Amount(shares * security.getSLast(), security.getCurrency());
    }
    return input.marketCaps().get(security.getIdSecuritycurrency());
  }

  private static Double rate(String currency, Input input) {
    if (input.mainCurrency().equals(currency)) {
      return 1.0;
    }
    Double rate = input.ratesToMain().get(currency);
    return rate == null || rate <= 0 || !Double.isFinite(rate) ? null : rate;
  }

  /** @return the tile, or null when the capitalisation or its exchange rate is missing or not a positive amount */
  private static Tile createTile(Security security, Input input) {
    Amount amount = marketCap(security, input);
    Double rate = amount == null ? null : rate(amount.currency(), input);
    if (rate == null) {
      return null;
    }
    double capMC = amount.value() * rate;
    return capMC > 0 && Double.isFinite(capMC) ? new Tile(security, capMC) : null;
  }

  private static String missingValueReason(Security security, Input input) {
    Amount amount = marketCap(security, input);
    return amount == null || amount.value() <= 0 || rate(amount.currency(), input) != null ? REASON_MARKET_CAP_MISSING
        : REASON_PRICE_MISSING;
  }

  private static List<WatchlistTreemapNode> createNodes(List<Tile> tiles, Input input) {
    if (tiles.isEmpty()) {
      return List.of();
    }
    Map<Integer, List<Tile>> tilesByAssetclass = new LinkedHashMap<>();
    tiles.forEach(tile -> tilesByAssetclass
        .computeIfAbsent(tile.security().getAssetClass().getIdAssetClass(), _ -> new ArrayList<>()).add(tile));
    List<WatchlistTreemapNode> nodes = new ArrayList<>();
    nodes.add(new WatchlistTreemapNode(ROOT_ID, null, WatchlistTreemapNodeType.ROOT, null, 0, totalCap(tiles),
        weightedChange(tiles), null, null, null, null));
    tilesByAssetclass.values().stream().sorted(Comparator.comparingDouble(WatchlistTreemapBuilder::totalCap).reversed())
        .forEach(group -> addGroup(nodes, group, input.language()));
    return nodes;
  }

  private static void addGroup(List<WatchlistTreemapNode> nodes, List<Tile> tiles, Language language) {
    Assetclass assetclass = tiles.getFirst().security().getAssetClass();
    String groupId = GROUP_PREFIX + assetclass.getIdAssetClass();
    nodes.add(new WatchlistTreemapNode(groupId, ROOT_ID, WatchlistTreemapNodeType.GROUP,
        groupLabel(assetclass, language), 0, totalCap(tiles), weightedChange(tiles), null, null, null, null));
    tiles.stream().sorted(Comparator.comparingDouble(Tile::capMC).reversed()).forEach(tile -> {
      Security security = tile.security();
      nodes.add(new WatchlistTreemapNode(SECURITY_PREFIX + security.getIdSecuritycurrency(), groupId,
          WatchlistTreemapNodeType.SECURITY, security.getName(), tile.capMC(), tile.capMC(),
          security.getSChangePercentage(), security.getIdSecuritycurrency(), security.getCurrency(),
          security.getSTimestamp(), security.getResolvedLogo()));
    });
  }

  private static String groupLabel(Assetclass assetclass, Language language) {
    String label = assetclass.getSubCategoryByLanguage(language);
    return label != null ? label : assetclass.getSubCategoryNLS().getMap().values().stream().findFirst().orElse(null);
  }

  private static double totalCap(List<Tile> tiles) {
    return tiles.stream().mapToDouble(Tile::capMC).sum();
  }

  private static double weightedChange(List<Tile> tiles) {
    double weighted = tiles.stream().mapToDouble(t -> t.capMC() * t.security().getSChangePercentage()).sum();
    return DataBusinessHelper.roundPercentage(weighted / totalCap(tiles));
  }
}
