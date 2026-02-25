/**
 * Determines which day within a month/year period a standing order executes on.
 * Only relevant when the repeat unit is MONTHS or YEARS; ignored for DAYS-based intervals.
 */
export enum PeriodDayPosition {
  /** Use the explicit dayOfExecution value (1-28). */
  SPECIFIC_DAY = 0,
  /** Execute on the first day of the period month. */
  FIRST_DAY = 1,
  /** Execute on the last day of the period month. */
  LAST_DAY = 2
}
