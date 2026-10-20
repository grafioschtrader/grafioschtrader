package grafioschtrader.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ends a tool call with an error result. The message is an NLS key that is translated into the language of the user;
 * the extra fields are added to the error content as they are.
 */
public class McpToolException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final McpErrorCode code;
  private final String messageKey;
  private final transient Object[] messageArgs;
  private final transient Map<String, Object> extras = new LinkedHashMap<>();

  public McpToolException(McpErrorCode code, String messageKey, Object... messageArgs) {
    super(code + " " + messageKey);
    this.code = code;
    this.messageKey = messageKey;
    this.messageArgs = messageArgs;
  }

  /**
   * A parameter of the client was rejected.
   *
   * @param parameter the name of the parameter as the tool declares it
   * @param reason    a short English description of the accepted values
   */
  public static McpToolException invalidArgument(String parameter, String reason) {
    return new McpToolException(McpErrorCode.INVALID_ARGUMENT, "gt.mcp.invalid.argument", parameter, reason)
        .with("parameter", parameter);
  }

  /**
   * An id passed by the client does not belong to the home tenant or names an instrument the tenant cannot see.
   *
   * @param parameter the name of the parameter that carried the id
   */
  public static McpToolException notOwned(String parameter) {
    return new McpToolException(McpErrorCode.NOT_OWNED, "gt.mcp.not.owned", parameter).with("parameter", parameter);
  }

  public McpToolException with(String field, Object value) {
    extras.put(field, value);
    return this;
  }

  public McpErrorCode getCode() {
    return code;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public Object[] getMessageArgs() {
    return messageArgs;
  }

  public Map<String, Object> getExtras() {
    return extras;
  }
}
