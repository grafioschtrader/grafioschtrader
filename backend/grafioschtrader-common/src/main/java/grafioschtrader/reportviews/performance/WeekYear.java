package grafioschtrader.reportviews.performance;

public enum WeekYear {
  WM_WEEK((byte) 0), WM_YEAR((byte) 1);

  private final Byte value;

  private WeekYear(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static WeekYear getWeekMonth(byte value) {
    for (WeekYear weekMonth : WeekYear.values()) {
      if (weekMonth.getValue() == value) {
        return weekMonth;
      }
    }
    return null;
  }
}
