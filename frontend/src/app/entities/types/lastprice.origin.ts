/**
 * Tells where the last price shown for an instrument comes from.
 *
 * An instrument that no longer receives intraday data still has to be valued, in which case the newest historical
 * closing price takes the place of the intraday price. Whether that closing price was really traded or was produced by
 * filling gaps decides how much the shown number can be trusted, which is why the two are told apart.
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/LastpriceOrigin.java
 */
export enum LastpriceOrigin {
  INTRADAY = 'INTRADAY',
  HISTORY_CLOSE = 'HISTORY_CLOSE',
  HISTORY_INTERPOLATED = 'HISTORY_INTERPOLATED'
}
