package grafioschtrader.types;

/**
 * Outcome of moving a single instrument from one watchlist to another.
 *
 * The status exists so the client does not have to pre-check whether the target watchlist already contains the
 * instrument. Without it the client would need an additional request per drag-and-drop operation, which quickly
 * exhausts the per-user request rate limit. It is transferred as a plain name over REST and never persisted.
 */
public enum WatchlistMoveStatus {

  /** The instrument was moved from the source to the target watchlist. */
  MOVED,

  /**
   * Nothing was changed because the target watchlist already contains the instrument. An instrument may exist only once
   * per watchlist, so the move would violate the primary key of {@code watchlist_sec_cur}.
   */
  ALREADY_IN_TARGET,

  /**
   * Nothing was changed because the source watchlist does not contain the instrument. This normally indicates a stale
   * client view, for example when the instrument was removed in another browser tab.
   */
  NOT_IN_SOURCE;
}
