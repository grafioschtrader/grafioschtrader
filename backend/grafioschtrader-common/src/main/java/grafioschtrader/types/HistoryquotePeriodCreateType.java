package grafioschtrader.types;

public enum HistoryquotePeriodCreateType {
  SYSTEM_CREATED((byte) 0), USER_CREATED((byte) 1);

  private final Byte value;

  private HistoryquotePeriodCreateType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static HistoryquotePeriodCreateType getHistoryquoteCreateType(byte value) {
    for (HistoryquotePeriodCreateType historyquotePeriodCreateType : HistoryquotePeriodCreateType.values()) {
      if (historyquotePeriodCreateType.getValue() == value) {
        return historyquotePeriodCreateType;
      }
    }
    return null;
  }

}
