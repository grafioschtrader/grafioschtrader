package grafioschtrader.types;

import java.util.Arrays;

public enum ProgressStateType {
  // It is waiting to be processed
  WAITING((byte) 0),
  // Was processed
  PROCESSED((byte) 1),
  // The processing failed
  FAILED((byte) 2),
  // Task cannot be found
  TASK_NOT_FOUND((byte) 3);

  private final Byte value;

  private ProgressStateType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static ProgressStateType getProgressStateTypeByValue(byte value) {
    return Arrays.stream(ProgressStateType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }

}
