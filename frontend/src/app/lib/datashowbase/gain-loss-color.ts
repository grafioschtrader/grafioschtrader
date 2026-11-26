import type { ColumnConfig } from './column.config';

/**
 * Decides whether a cell shows its value in the gain color. Green is reserved for columns marked with
 * `ColumnConfig.gainLossColor`, the results and changes such as gains, returns and daily changes; a positive balance,
 * fee or quantity stays neutral. The decision is taken on the displayed text, so a value rounded to zero stays neutral
 * and columns computed through `fieldValueFN` need no raw value. The red side is decided by the callers as before.
 */
export class GainLossColor {
  /**
   * Whether the displayed text shows a value above zero: no leading minus sign and at least one non-zero digit.
   *
   * @param text - The formatted cell value
   * @returns True for a positive value
   */
  static isPositiveText(text: string | number | null | undefined): boolean {
    if (text == null) {
      return false;
    }
    const trimmed = String(text).trim();
    return trimmed !== '' && !trimmed.startsWith('-') && !trimmed.startsWith('−') && /[1-9]/.test(trimmed);
  }

  /**
   * Whether a cell is shown in the gain color.
   *
   * @param field - The column; only a column with `gainLossColor` turns green
   * @param text - The formatted cell value
   * @param negative - The negative decision of the caller, which colors the cell red
   * @returns True when the cell gets the class `gt-gain`
   */
  static isGain(field: ColumnConfig, text: string | number | null | undefined, negative: boolean): boolean {
    return field.gainLossColor === true && !negative && GainLossColor.isPositiveText(text);
  }
}
