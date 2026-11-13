package grafioschtrader.types;

/** Level of a node of the watchlist market map. */
public enum WatchlistTreemapNodeType {
  /** The whole watchlist */
  ROOT,
  /** The stocks of one asset class, labelled with its sub category */
  GROUP,
  /** A single stock */
  SECURITY
}
