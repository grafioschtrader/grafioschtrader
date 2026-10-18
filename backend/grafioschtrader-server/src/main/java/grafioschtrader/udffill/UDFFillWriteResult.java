package grafioschtrader.udffill;

/**
 * What {@link UDFFillWriter} did with one cell. The fill runs count these results.
 */
public enum UDFFillWriteResult {
  /** The value was written and the cell is automatic. */
  WRITTEN,
  /** An existing value equal to the computed one was adopted as automatic. */
  ADOPTED,
  /** The automatic value is already the computed one. */
  UNCHANGED,
  /** The source no longer produces a value, and the unedited automatic value was removed. */
  REMOVED,
  /** The cell holds a value the user typed or edited; it is not touched. */
  MANUAL,
  /** The user took the cell over; it is not touched. */
  LOCKED,
  /** The source has no value and the cell is empty. */
  NOT_FOUND,
  /** A technical failure; nothing was changed. */
  RETRY,
  /** The field was deleted, switched to manual or got a new rule after the outcome was computed. */
  STALE
}
