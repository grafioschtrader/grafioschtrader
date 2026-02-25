/**
 * Time unit for standing order repeat intervals. Combined with repeatInterval, this determines
 * how frequently a standing order generates transactions (e.g. every 20 days, monthly, quarterly, yearly).
 */
export enum RepeatUnit {
  /** Repeat every N days. Day-of-execution and period-day-position are ignored. */
  DAYS = 0,
  /** Repeat every N months. Supports day-of-execution (1-28) or first/last day positioning. */
  MONTHS = 1,
  /** Repeat every N years. Supports month-of-execution (1-12) and day-of-execution or first/last day positioning. */
  YEARS = 2
}
