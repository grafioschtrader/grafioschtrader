package grafioschtrader.udffill;

/**
 * What a fill source answered for one security, computed outside any transaction and handed to
 * {@link UDFFillWriter#write(grafioschtrader.entities.UDFMetadataSecurity, int, UDFFillOutcome)}.
 */
public sealed interface UDFFillOutcome {

  /**
   * The source computed a value.
   *
   * @param value     the value in canonical form, never empty
   * @param sourceUrl optional page from which the value was taken
   */
  record Value(String value, String sourceUrl) implements UDFFillOutcome {
  }

  /** The source answered definitively that it has no value. Only this outcome removes an automatic value. */
  record None() implements UDFFillOutcome {
  }

  /**
   * A technical failure; nothing is changed and the cell is asked again at the next run.
   *
   * @param reason a description for the log
   */
  record Retryable(String reason) implements UDFFillOutcome {
  }
}
