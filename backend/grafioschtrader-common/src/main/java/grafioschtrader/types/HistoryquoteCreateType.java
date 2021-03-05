package grafioschtrader.types;

public enum HistoryquoteCreateType {
  // Created by a feed connector
  CONNECTOR_CREATED((byte) 0),
  // Only used for currencies, since account transaction can happened at the
  // weekend we used this for table joins.
  FILLED_NON_TRADE_DAY((byte) 1),
  // History quote can be imported thru the user interface
  MANUAL_IMPORTED((byte) 2),
  // It can be used for non active security which missing some data on trading
  // days.
  FILLED_CLOSED_LINEAR_TRADING_DAY((byte) 3),
  // For a derived security, its history quotes are calculated
  CALCULATED((byte) 4),
  // Added or modified by the user
  ADD_MODIFIED_USER((byte) 5);

  private final Byte value;

  private HistoryquoteCreateType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static HistoryquoteCreateType getHistoryquoteCreateType(byte value) {
    for (HistoryquoteCreateType historyquoteCreateType : HistoryquoteCreateType.values()) {
      if (historyquoteCreateType.getValue() == value) {
        return historyquoteCreateType;
      }
    }
    return null;
  }
}
