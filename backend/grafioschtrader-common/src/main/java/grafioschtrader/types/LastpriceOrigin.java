package grafioschtrader.types;

/**
 * Tells where the last price of an instrument shown to the user comes from.
 * <p>
 * An instrument that no longer receives intraday data - because it was delisted, the issuer went bankrupt or its
 * connector was switched to one that stopped delivering - still has to be valued. In that case the newest historical
 * closing price takes the place of the intraday price. Whether that closing price was really traded or was created by
 * filling a gap decides how much the displayed number can be trusted, which is why the two are told apart.
 * </p>
 */
public enum LastpriceOrigin {

  /** The price was delivered by the intraday connector of the instrument. */
  INTRADAY,

  /** The price is the newest historical closing price a connector really delivered. */
  HISTORY_CLOSE,

  /**
   * The price is a historical closing price that was not traded but calculated, either by the linear gap filling of the
   * user ({@link HistoryquoteCreateType#FILLED_CLOSED_LINEAR_TRADING_DAY}) or by the gap filler of a connector
   * ({@link HistoryquoteCreateType#FILL_GAP_BY_CONNECTOR}).
   */
  HISTORY_INTERPOLATED;

  /**
   * Maps the create type of a history quote to the origin of a price taken from it.
   *
   * @param createType ordinal of a {@link HistoryquoteCreateType}, may be null for a row written before the column
   *                   existed
   * @return {@link #HISTORY_INTERPOLATED} for a fabricated quote, {@link #HISTORY_CLOSE} otherwise
   */
  public static LastpriceOrigin ofHistoryquoteCreateType(Integer createType) {
    if (createType != null
        && (createType.byteValue() == HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY.getValue()
            || createType.byteValue() == HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR.getValue())) {
      return HISTORY_INTERPOLATED;
    }
    return HISTORY_CLOSE;
  }
}
