package grafioschtrader.algo;

/**
 * DTO for auto-generating a complete AlgoTop hierarchy from the instruments of the linked watchlist. Extends
 * {@link AlgoTopCreate} so the existing {@code saveOnlyAttributes()} dispatch can detect this subclass.
 *
 * <p>
 * It carries no field of its own: the name and the watchlist are inherited from {@code AlgoTop}, and
 * {@code assetclassPercentageList} stays unused because the buckets and their weights are derived from the watchlist
 * rather than entered. A watchlist holds no amounts, so both levels are weighted equally - the asset classes among
 * themselves and the instruments within their asset class.
 * </p>
 */
public class AlgoTopCreateFromWatchlist extends AlgoTopCreate {

  private static final long serialVersionUID = 1L;
}
