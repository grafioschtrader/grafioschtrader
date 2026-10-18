package grafioschtrader.udffill.resolver;

/**
 * What a resolver answered for one security. {@link Found} maps to the writer outcome {@code Value}, {@link NotFound}
 * to {@code None} and {@link Retryable} to {@code Retryable}.
 */
public sealed interface ResolveOutcome {

  /**
   * The source has a value.
   *
   * @param value     the value
   * @param sourceUrl the page of the source that names the value
   */
  record Found(String value, String sourceUrl) implements ResolveOutcome {
  }

  /**
   * The source answered definitively that it has no value.
   *
   * @param reason why
   */
  record NotFound(NotFoundReason reason) implements ResolveOutcome {
  }

  /**
   * A technical failure; the cell is asked again at the next run.
   *
   * @param reason a description for the log
   */
  record Retryable(String reason) implements ResolveOutcome {
  }

  /** Why a resolver has no value. */
  enum NotFoundReason {
    /** The security has no ISIN, so nothing was asked. */
    NO_ISIN,
    /** No item carries the ISIN. */
    ZERO_HITS,
    /** More than one item carries the ISIN. */
    AMBIGUOUS,
    /** The item has no article in any of the requested languages. */
    NO_SITELINK,
    /** The value found fails the validation of the field, for example a URL longer than 254 characters. */
    INVALID
  }
}
