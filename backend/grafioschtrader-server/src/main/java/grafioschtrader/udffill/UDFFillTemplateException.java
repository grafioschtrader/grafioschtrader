package grafioschtrader.udffill;

/**
 * A fill rule of the fill source {@code TEMPLATE} that does not parse. The exception names the line and the position
 * within that line, both counted from 1, so that the user can find the fault in the edit form.
 */
public class UDFFillTemplateException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  /**
   * The kinds of parse errors. Each carries the NLS key of its translated message, which takes the line number, the
   * position and the offending token as arguments {0}, {1} and {2}.
   */
  public enum Reason {
    UNKNOWN_PLACEHOLDER("gt.udf.fill.rule.unknown.placeholder"), UNKNOWN_FILTER("gt.udf.fill.rule.unknown.filter"),
    INVALID_FILTER_ARGUMENT("gt.udf.fill.rule.invalid.filter.argument"),
    DUPLICATE_MAP_KEY("gt.udf.fill.rule.duplicate.map.key"),
    UNKNOWN_CONDITION_KEY("gt.udf.fill.rule.unknown.condition.key"),
    DUPLICATE_CONDITION_KEY("gt.udf.fill.rule.duplicate.condition.key"),
    INVALID_CONDITION("gt.udf.fill.rule.invalid.condition"),
    CONDITION_SPACE_MISSING("gt.udf.fill.rule.condition.space.missing"),
    UNBALANCED_BRACE("gt.udf.fill.rule.unbalanced.brace"), UNBALANCED_BRACKET("gt.udf.fill.rule.unbalanced.bracket"),
    EMPTY_TEMPLATE("gt.udf.fill.rule.empty.template"), NO_LINE("gt.udf.fill.rule.no.line");

    private final String nlsKey;

    private Reason(String nlsKey) {
      this.nlsKey = nlsKey;
    }

    public String getNlsKey() {
      return nlsKey;
    }
  }

  private final Reason reason;
  private final int line;
  private final int position;
  private final String token;

  public UDFFillTemplateException(Reason reason, int line, int position, String token) {
    super("Line " + line + ", position " + position + ": " + reason + (token == null ? "" : " '" + token + "'"));
    this.reason = reason;
    this.line = line;
    this.position = position;
    this.token = token;
  }

  public Reason getReason() {
    return reason;
  }

  public int getLine() {
    return line;
  }

  public int getPosition() {
    return position;
  }

  public String getToken() {
    return token;
  }

  /**
   * Returns the arguments of the translated message.
   *
   * @return line number, position and token, the token as an empty string when there is none
   */
  public Object[] getMessageArgs() {
    return new Object[] { line, position, token == null ? "" : token };
  }
}
