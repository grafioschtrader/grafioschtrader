package grafiosch.gtnet.model;

import grafiosch.gtnet.IExchangeKindType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO that exposes exchange kind type metadata to the frontend. Allows the frontend to dynamically
 * build entity kind lists and determine per-kind capabilities (push support, syncability) without
 * hardcoding enum values.
 */
@Schema(description = """
    Metadata about an exchange kind type, including its name, numeric value, and capability flags.
    Sent to the frontend so it can dynamically build entity configurations.""")
public class ExchangeKindTypeInfo {

  @Schema(description = "Enum constant name, e.g. 'LAST_PRICE'")
  public String name;

  @Schema(description = "Byte value used for serialization, e.g. 0")
  public byte value;

  @Schema(description = "Whether this kind supports AC_PUSH_OPEN mode")
  public boolean supportsPush;

  @Schema(description = "Whether this kind participates in bulk synchronization")
  public boolean syncable;

  public ExchangeKindTypeInfo() {
  }

  public ExchangeKindTypeInfo(IExchangeKindType kind) {
    this.name = kind.name();
    this.value = kind.getValue();
    this.supportsPush = kind.supportsPush();
    this.syncable = kind.isSyncable();
  }
}
