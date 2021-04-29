package grafioschtrader.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CreateType {

  // Created by a feed connector
  CONNECTOR_CREATED((byte) 0),
  // Added or modified by the user
  ADD_MODIFIED_USER((byte) 5);

  private final Byte value;

  private CreateType(final Byte value) {
    this.value = value;
  }

  @JsonValue
  public Byte getValue() {
    return this.value;
  }

  @JsonCreator
  public static CreateType getCreateType(byte value) {
    for (CreateType createType : CreateType.values()) {
      if (createType.getValue() == value) {
        return createType;
      }
    }
    return null;
  }

}
