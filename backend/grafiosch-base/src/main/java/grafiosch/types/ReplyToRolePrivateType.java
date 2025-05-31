package grafiosch.types;

import java.util.Arrays;

public enum ReplyToRolePrivateType {
  REPLY_NORMAL((byte) 0), REPLY_AS_ROLE((byte) 1), REPLY_IS_PRIVATE((byte) 2);

  private final Byte value;

  private ReplyToRolePrivateType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static ReplyToRolePrivateType getReplyToRolePrivateTypeByValue(byte value) {
    return Arrays.stream(ReplyToRolePrivateType.values()).filter(e -> e.getValue().equals(value)).findFirst()
        .orElse(null);
  }
}
