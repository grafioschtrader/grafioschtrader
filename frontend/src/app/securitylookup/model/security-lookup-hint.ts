/** Suffix of an argument whose value is a translation key. */
const KEY_SUFFIX = 'Key';
/** Suffix of an argument whose value is a date 'yyyy-MM-dd'. */
const DATE_SUFFIX = 'Date';

/**
 * Turns the arguments of a lookup hint into the parameters of its translated text. An argument whose name ends in
 * 'Key' is translated and one ending in 'Date' is formatted; both fill the placeholder named without the suffix, e.g.
 * 'statusKey' fills '{{status}}'. Every other argument is used as it is.
 *
 * @param args - The arguments of the hint, may be undefined
 * @param translate - Translates a key into the language of the user
 * @param formatDate - Formats a date 'yyyy-MM-dd' in the format of the user
 * @returns The parameters for the translation of the hint text
 */
export function hintParams(
  args: { [name: string]: string },
  translate: (key: string) => string,
  formatDate: (isoDate: string) => string
): { [name: string]: string } {
  const params: { [name: string]: string } = {};
  Object.entries(args ?? {}).forEach(([name, value]) => {
    if (name.endsWith(KEY_SUFFIX)) {
      params[name.slice(0, -KEY_SUFFIX.length)] = translate(value);
    } else if (name.endsWith(DATE_SUFFIX)) {
      params[name.slice(0, -DATE_SUFFIX.length)] = formatDate(value);
    } else {
      params[name] = value;
    }
  });
  return params;
}

/** A hint prepared for the hint table: translated text, and the translation key of the field label. */
export interface LookupHintRow {
  severity: string;
  /** Translation key of the field label, e.g. ACTIVE_FROM_DATE */
  fieldKey: string;
  text: string;
}
