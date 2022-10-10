package grafioschtrader.types;

import io.swagger.v3.oas.annotations.media.Schema;

public enum LastpriceRightsCapabilityType {

  NONE((byte) 0), @Schema(description = "The is no acces for this remote domain.")
  CLOSED((byte) 1), OPEN((byte) 2);

  private final Byte value;

  private LastpriceRightsCapabilityType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static LastpriceRightsCapabilityType getOpenClosedType(byte value) {
    for (LastpriceRightsCapabilityType readWriteType : LastpriceRightsCapabilityType.values()) {
      if (readWriteType.getValue() == value) {
        return readWriteType;
      }
    }
    return null;
  }
}
