/** How a detail line formats its value. */
export type MobileDetailFormat =
  'text' | 'translate' | 'date' | 'dateTime' | 'price' | 'percentage' | 'units' | 'integer' | 'amount';

/** One labelled value, shown as a line with the label on the left and the value on the right. */
export interface MobileDetailEntry {
  labelKey: string;
  value: string | number;
  format: MobileDetailFormat;
  /** Currency of an amount. */
  currency?: string;
  /** Fraction digits of a price, as the backend decided them for the instrument. */
  fractionDigits?: number | null;
  /** The value is a change; gains and losses are colored. */
  signed?: boolean;
}

/** A titled group of detail lines. The title is a translation key, or a name such as that of an account. */
export interface MobileDetailSection {
  titleKey?: string;
  title?: string;
  entries: MobileDetailEntry[];
}

/**
 * A section of the given lines; lines without a value are left out.
 *
 * @param titleKey Translation key of the title
 * @param entries Lines, null for a line without a value
 * @returns The section, possibly without lines
 */
export function detailSection(titleKey: string, entries: (MobileDetailEntry | null)[]): MobileDetailSection {
  return { titleKey, entries: entries.filter((e): e is MobileDetailEntry => e !== null) };
}

/**
 * A detail line, or null when there is no value to show.
 *
 * @param labelKey Translation key of the label
 * @param value Value of the line
 * @param format How the value is formatted
 * @param options Currency, fraction digits and coloring of the value
 * @returns The line, or null for a missing or empty value
 */
export function detailEntry(
  labelKey: string,
  value: string | number | null | undefined,
  format: MobileDetailFormat,
  options: Pick<MobileDetailEntry, 'currency' | 'fractionDigits' | 'signed'> = {}
): MobileDetailEntry | null {
  return value == null || value === '' ? null : { labelKey, value, format, ...options };
}
