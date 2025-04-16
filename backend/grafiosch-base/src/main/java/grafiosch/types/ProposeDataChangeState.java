package grafiosch.types;

import java.util.Arrays;

/**
 * The state of a propose data change.
 */
public enum ProposeDataChangeState {
  // The propose data change is open.
  OPEN((byte) 0),
  // The propose data change is rejected.
  REJECT((byte) 1),
  // The propose data change is accepted
  ACCEPTED((byte) 2);

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
