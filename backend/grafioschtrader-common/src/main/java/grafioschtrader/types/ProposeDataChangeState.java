package grafioschtrader.types;

import java.util.Arrays;

public enum ProposeDataChangeState {
  OPEN((byte) 0), REJECT((byte) 1), ACCEPTED((byte) 2);

  private final Byte value;

  private ProposeDataChangeState(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static ProposeDataChangeState getProposeDataChangeStateByValue(byte value) {
    return Arrays.stream(ProposeDataChangeState.values()).filter(e -> e.getValue().equals(value)).findFirst()
        .orElse(null);
  }
}
