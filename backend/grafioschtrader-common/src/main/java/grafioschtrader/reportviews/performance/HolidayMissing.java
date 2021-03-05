package grafioschtrader.reportviews.performance;

public enum HolidayMissing {
  HM_NONE((byte) 0), HM_TRADING_DAY((byte) 1), HM_HOLIDAY((byte) 2), HM_HISTORY_DATA_MISSING((byte) 3);

  private final Byte value;

  private HolidayMissing(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static HolidayMissing getHolidayMissing(byte value) {
    for (HolidayMissing holidayMissing : HolidayMissing.values()) {
      if (holidayMissing.getValue() == value) {
        return holidayMissing;
      }
    }
    return null;
  }
}
