package grafioschtrader.evalex;

/** Validation error whose text is resolved in the requesting user's language at the write boundary. */
public class AlertExpressionException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;
  private final String key;
  private final Object[] arguments;

  public AlertExpressionException(String key, Object... arguments) {
    super(key);
    this.key = key;
    this.arguments = arguments;
  }

  public String getKey() {
    return key;
  }

  public Object[] getArguments() {
    return arguments.clone();
  }
}
