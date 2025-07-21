/**
 * Represents the state of a closed margin trading position, tracking whether the position has been closed
 * and the number of units that were closed. Used in margin trading operations to manage position lifecycle
 * and calculate remaining exposure after partial or complete position closures.
 */
export interface ClosedMarginPosition {
  /** Indicates whether the margin position has any closed units or active closing transactions */
  hasPosition: boolean;

  /** The total number of units that have been closed for this margin position */
  closedUnits: number;
}
