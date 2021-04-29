package grafioschtrader.usertask;

import java.util.Arrays;

public enum UserTaskType {
  /**
   * Used for the User violations (request per period or security breach).
   */
  RELEASE_LOGOUT((byte) 0),
  /**
   * Used for user entity change limit, for example number of CUD on stock
   * exchange
   */
  LIMIT_CUD_CHANGE((byte) 1);

  private final Byte value;

  private UserTaskType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static UserTaskType getUserTaskTypeByValue(byte value) {
    return Arrays.stream(UserTaskType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
