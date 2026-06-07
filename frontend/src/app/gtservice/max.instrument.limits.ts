/**
 * Per-instrument editing limits provided by the backend (sourced from globalparameters). Used by the Security edit
 * dialog to cap the number of split entries and history-quote periods a user may add.
 */
export interface MaxInstrumentLimits {
  /** Maximum number of split entries per instrument (gt.max.instrument.splits). */
  maxInstrumentSplits: number;
  /** Maximum number of history-quote periods per instrument (gt.max.instrument.historyquote.periods). */
  maxInstrumentHistoryquotePeriods: number;
}
