package grafioschtrader.algo.rule;

public enum AndOrNotType {
  AN_AND((byte) 1), AN_OR((byte) 2), AN_NOT((byte) 3);

  private final Byte value;

  private AndOrNotType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AndOrNotType getAndOrNotType(byte value) {
    for (AndOrNotType andOrNot : AndOrNotType.values()) {
      if (andOrNot.getValue() == value) {
        return andOrNot;
      }
    }
    return null;
  }

}
