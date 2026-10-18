package grafioschtrader.udffill;

import java.util.Objects;

import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillOutcome.None;
import grafioschtrader.udffill.UDFFillOutcome.Retryable;
import grafioschtrader.udffill.UDFFillOutcome.Value;

/**
 * The decision table of the write rule "manual values always win". It decides from the fill row, the outcome of the
 * source and the current value of the cell what happens to the value and to the fill row. It is pure, so that the
 * watchlist load can decide in memory whether a write is needed at all, and {@link UDFFillWriter} applies the same
 * decision under the row lock.
 *
 * @param result      what is reported for the cell
 * @param valueAction what happens to key {@code f<id>} in {@code udf_data}
 * @param rowAction   what happens to the fill row
 */
public record UDFFillDecision(UDFFillWriteResult result, ValueAction valueAction, RowAction rowAction) {

  /** Change of the value in {@code udf_data}. */
  public enum ValueAction {
    NONE, WRITE, REMOVE
  }

  /** Change of the row in {@code udf_data_fill}. */
  public enum RowAction {
    NONE, UPSERT_APPLIED, UPSERT_NOT_FOUND, DELETE
  }

  private static UDFFillDecision of(UDFFillWriteResult result, ValueAction valueAction, RowAction rowAction) {
    return new UDFFillDecision(result, valueAction, rowAction);
  }

  /**
   * Decides for one cell.
   *
   * @param state       the state of the fill row, or null when there is none
   * @param filledValue the filled value of the fill row, or null
   * @param outcome     the outcome of the source
   * @param cur         the canonical current value of the cell, null or empty when the cell is empty
   * @param template    true for the fill source TEMPLATE, which never stores a NOT_FOUND row
   * @return the decision
   */
  public static UDFFillDecision decide(UDFFillState state, String filledValue, UDFFillOutcome outcome, String cur,
      boolean template) {
    if (state == UDFFillState.LOCKED) {
      return of(UDFFillWriteResult.LOCKED, ValueAction.NONE, RowAction.NONE);
    }
    return switch (outcome) {
    case Retryable _ -> of(UDFFillWriteResult.RETRY, ValueAction.NONE, RowAction.NONE);
    case Value value -> decideValue(state, filledValue, value.value(), cur);
    case None _ -> decideNone(state, filledValue, cur, template);
    };
  }

  private static UDFFillDecision decideValue(UDFFillState state, String filledValue, String v, String cur) {
    boolean curEmpty = isEmpty(cur);
    if (state == UDFFillState.APPLIED) {
      if (Objects.equals(filledValue, cur)) {
        return v.equals(cur) ? of(UDFFillWriteResult.UNCHANGED, ValueAction.NONE, RowAction.UPSERT_APPLIED)
            : of(UDFFillWriteResult.WRITTEN, ValueAction.WRITE, RowAction.UPSERT_APPLIED);
      }
      return curEmpty ? of(UDFFillWriteResult.WRITTEN, ValueAction.WRITE, RowAction.UPSERT_APPLIED)
          : of(UDFFillWriteResult.MANUAL, ValueAction.NONE, RowAction.DELETE);
    }
    // No row, NOT_FOUND or a state of a language model proposal
    if (curEmpty) {
      return of(UDFFillWriteResult.WRITTEN, ValueAction.WRITE, RowAction.UPSERT_APPLIED);
    }
    if (v.equals(cur)) {
      return of(UDFFillWriteResult.ADOPTED, ValueAction.NONE, RowAction.UPSERT_APPLIED);
    }
    return of(UDFFillWriteResult.MANUAL, ValueAction.NONE, state == null ? RowAction.NONE : RowAction.DELETE);
  }

  private static UDFFillDecision decideNone(UDFFillState state, String filledValue, String cur, boolean template) {
    boolean curEmpty = isEmpty(cur);
    RowAction notFoundRow = template ? (state == null ? RowAction.NONE : RowAction.DELETE) : RowAction.UPSERT_NOT_FOUND;
    if (state == UDFFillState.APPLIED && Objects.equals(filledValue, cur)) {
      return of(UDFFillWriteResult.REMOVED, ValueAction.REMOVE,
          template ? RowAction.DELETE : RowAction.UPSERT_NOT_FOUND);
    }
    if (curEmpty) {
      return of(UDFFillWriteResult.NOT_FOUND, ValueAction.NONE, notFoundRow);
    }
    return of(UDFFillWriteResult.MANUAL, ValueAction.NONE, state == null ? RowAction.NONE : RowAction.DELETE);
  }

  /**
   * Tells whether applying this decision changes anything in the database.
   *
   * @param rowVersion     the rule version of the existing fill row, or null when there is none
   * @param currentVersion the current fill rule version of the field
   * @return false when neither the value nor the fill row would change, so no write transaction is needed
   */
  public boolean changesSomething(Integer rowVersion, int currentVersion) {
    if (valueAction != ValueAction.NONE || rowAction == RowAction.DELETE || rowAction == RowAction.UPSERT_NOT_FOUND) {
      return true;
    }
    return rowAction == RowAction.UPSERT_APPLIED
        && (result != UDFFillWriteResult.UNCHANGED || rowVersion == null || rowVersion != currentVersion);
  }

  /**
   * Tells whether a value is empty in the sense of the fill: absent, null or the empty string.
   */
  public static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }
}
