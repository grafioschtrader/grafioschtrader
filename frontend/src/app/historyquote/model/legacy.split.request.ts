/**
 * Body of POST /historyquotes/legacy/{idSecuritycurrency}/split. Only legacy rows whose date is
 * strictly before {@code splitDate} are adjusted. OHLC values are multiplied by
 * {@code fromFactor / toFactor}; volume by {@code toFactor / fromFactor}.
 */
export interface LegacySplitRequest {
  splitDate: string;
  fromFactor: number;
  toFactor: number;
}
