package grafioschtrader.algo.rule;

public enum AndOrNot {
  AN_AND((byte) 1), AN_OR((byte) 2), AN_NOT((byte) 3);

  private final Byte value;

  private AndOrNot(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AndOrNot getAndOrNot(byte value) {
    for (AndOrNot andOrNot : AndOrNot.values()) {
      if (andOrNot.getValue() == value) {
        return andOrNot;
      }
    }
    return null;
  }

}
