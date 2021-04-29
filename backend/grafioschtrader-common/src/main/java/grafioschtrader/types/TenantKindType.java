package grafioschtrader.types;

import java.util.Arrays;

public enum TenantKindType {

  // Created by when a user is registered
  MAIN((byte) 0),
  // Tenant only used for a simulation enviroment
  SIMULATION_COPY((byte) 1);

  private final Byte value;

  private TenantKindType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TenantKindType getTenantKindTypeByValue(byte value) {
    return Arrays.stream(TenantKindType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }

}
