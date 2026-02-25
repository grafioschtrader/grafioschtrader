/**
 * Controls how a standing order execution date is shifted when it falls on a weekend.
 */
export enum WeekendAdjustType {
  /** Shift the execution date to the preceding Friday. */
  BEFORE = 0,
  /** Shift the execution date to the following Monday. */
  AFTER = 1
}
