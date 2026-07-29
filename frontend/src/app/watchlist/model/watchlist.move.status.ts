/**
 * Outcome of moving a single instrument from one watchlist to another, as returned by
 * PUT watchlist/{source}/moveto/{target}/securitycurrency/{id}.
 *
 * The backend reports the outcome so the client does not need an extra request to check whether the target watchlist
 * already contains the instrument. Mirrors the Java enum grafioschtrader.types.WatchlistMoveStatus.
 */
export enum WatchlistMoveStatus {
  /** The instrument was moved from the source to the target watchlist. */
  MOVED = 'MOVED',
  /** Nothing was changed, the target watchlist already contains the instrument. */
  ALREADY_IN_TARGET = 'ALREADY_IN_TARGET',
  /** Nothing was changed, the source watchlist does not contain the instrument (stale client view). */
  NOT_IN_SOURCE = 'NOT_IN_SOURCE'
}
