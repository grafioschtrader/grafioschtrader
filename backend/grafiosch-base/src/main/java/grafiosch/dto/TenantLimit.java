package grafiosch.dto;

import java.util.regex.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The limits definition for a certain entity")
public class TenantLimit {
  @Schema(description = "Maximal number of entities in this limit definition")
  public int limit;
  @Schema(description = "Actual number of entities in this limit definition")
  public int actual;
  @Schema(description = "Error message key will appear when the limit is exceeded")
  public String msgKey;
  @Schema(description = "Name of the entity to which the limit refers")
  public String className;

  public TenantLimit(String msgKey, int limit, int actual, String className) {
    this.limit = limit;
    this.actual = actual;
    this.msgKey = msgKey;
    this.className = className;
  }

  public TenantLimit(int limit, int actual, String key, String className) {
    this(key.substring(3).toUpperCase().replaceAll(Pattern.quote("."), "_"), limit, actual, className);
  }

}
