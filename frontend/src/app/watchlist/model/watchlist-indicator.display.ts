import { SecuritycurrencyIndicatorGroup, WatchlistIndicatorColumn } from './watchlist-indicator';

/** Translates one key; the callers pass `translateService.instant`. */
export type IndicatorTranslate = (key: string) => string;

/**
 * Row field holding the value of an indicator column. The indicator tab copies every cell value of the report into its
 * rows under this name, so table columns and the expanded row read the same field.
 *
 * @param idWatchlistIndicatorColumn - id of the indicator column
 * @returns the synthetic field name
 */
export function indicatorField(idWatchlistIndicatorColumn: number): `ind_${number}` {
  return `ind_${idWatchlistIndicatorColumn}`;
}

/**
 * Style of an indicator value against the lower and upper mark of its column. A neutral column highlights the
 * background, the other two colour the text green or red depending on which mark counts as positive.
 *
 * @param column - the indicator column with its marks
 * @param value - the cell value; only numbers are marked
 * @returns the style, or null when the value lies between the marks or is not a number
 */
export function indicatorMarkStyle(column: WatchlistIndicatorColumn, value: unknown): { [key: string]: string } | null {
  if (typeof value !== 'number') return null;
  const low = column.lowerMark != null && value <= column.lowerMark;
  const high = column.upperMark != null && value >= column.upperMark;
  if (!low && !high) return null;
  if (column.markColors === 'NEUTRAL') return { 'background-color': 'rgba(234, 179, 8, 0.30)' };
  const positive = column.markColors === 'LOW_POSITIVE_HIGH_NEGATIVE' ? low : high;
  return { color: positive ? 'green' : 'red' };
}

/**
 * Whether a cell needs the info icon: when it has no value, or when a boolean result was evaluated on a common date
 * with its benchmark, which the bare check mark cannot show.
 *
 * @param group - the indicator report
 * @param column - the indicator column
 * @param idSecuritycurrency - the instrument of the row
 * @returns true when the cell gets the info icon
 */
export function indicatorNeedsMark(
  group: SecuritycurrencyIndicatorGroup | undefined,
  column: WatchlistIndicatorColumn,
  idSecuritycurrency: number | null | undefined
): boolean {
  const columnId = column.idWatchlistIndicatorColumn;
  if (!group || columnId == null || idSecuritycurrency == null) return false;
  const cell = group.cells[idSecuritycurrency]?.[columnId];
  return !!cell && (cell.status !== 'OK' || (column.resultType === 'BOOLEAN' && !!cell.lastCommonDate));
}

/**
 * Tooltip of an indicator cell: the cause when it has no value, otherwise the last date common with the benchmark,
 * the currency pair when instrument and benchmark are quoted differently, and the origin of every fundamental value
 * the expression read.
 *
 * @param group - the indicator report
 * @param column - the indicator column
 * @param idSecuritycurrency - the instrument of the row
 * @param currency - the quotation currency of the instrument
 * @param t - translation of a key
 * @returns the tooltip text, empty when there is nothing to tell
 */
export function indicatorTooltip(
  group: SecuritycurrencyIndicatorGroup | undefined,
  column: WatchlistIndicatorColumn,
  idSecuritycurrency: number | null | undefined,
  currency: string | null | undefined,
  t: IndicatorTranslate
): string {
  const columnId = column.idWatchlistIndicatorColumn;
  if (!group || idSecuritycurrency == null || columnId == null) return '';
  const cell = group.cells[idSecuritycurrency]?.[columnId];
  if (!cell) return '';
  if (cell.status !== 'OK') return t('WIC_STATUS_' + cell.status);
  const benchmark = column.idBenchmark == null ? undefined : group.benchmarks[column.idBenchmark];
  return (
    (cell.lastCommonDate ?? '') +
    (benchmark && benchmark.currency !== currency ? ' ' + currency + ' / ' + benchmark.currency : '') +
    fundamentalTooltip(group, columnId, idSecuritycurrency, t)
  );
}

/**
 * One line per fundamental variable the column reads: source, basis, period end, grade, fetch date and, for a
 * converted value, the original currency and the exchange rate.
 */
function fundamentalTooltip(
  group: SecuritycurrencyIndicatorGroup,
  columnId: number,
  idSecuritycurrency: number,
  t: IndicatorTranslate
): string {
  const variables = group.columnFundamentalVariables?.[columnId] ?? [];
  const infos = group.fundamentalInfo?.[idSecuritycurrency] ?? {};
  return variables
    .filter((variable) => infos[variable])
    .map((variable) => {
      const info = infos[variable];
      const parts = [info.source];
      if (info.basis) parts.push(t('WIC_FUNDAMENTAL_' + info.basis));
      if (info.periodEnd) parts.push(t('WIC_FUNDAMENTAL_PERIOD') + ' ' + info.periodEnd);
      parts.push(t('WIC_GRADE_' + info.state));
      if (info.fetchedDate) parts.push(t('WIC_FUNDAMENTAL_FETCHED') + ' ' + info.fetchedDate);
      if (info.rate != null) parts.push(t('WIC_FUNDAMENTAL_RATE') + ' ' + info.currency + ' × ' + info.rate);
      return '\n' + variable + ': ' + parts.join(', ');
    })
    .join('');
}
