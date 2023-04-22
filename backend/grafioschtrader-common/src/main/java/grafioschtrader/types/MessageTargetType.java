package grafioschtrader.types;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description ="The selection of possible message channels for a message")
public enum MessageTargetType {
  // No message is send
  NO_MAIL((byte) 0),
  // User only internal mail system
  INTERNAL_MAIL((byte) 1),
  // Send only to external email system
  EXTERNAL_MAIL((byte) 2),
  // Sent to the internal and external mail system
  INTERNAL_AND_EXTERNAL_MAIL((byte) 3);

  private final Byte value;

  private MessageTargetType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static MessageTargetType getMessageTargetTypeByValue(byte value) {
    for (MessageTargetType messageTargetType : MessageTargetType.values()) {
      if (messageTargetType.getValue() == value) {
        return messageTargetType;
      }
    }
    return null;
  }
}
