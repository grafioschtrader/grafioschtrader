/**
 * A translation dictionary as delivered by the backend language endpoint or kept in `optimus.translations.ts`: text
 * values, string arrays (Optimus day and month names) and nested namespaces such as `GT_FILTER` or `optimus`.
 */
export type TranslationDictionary = Record<string, unknown>;

/**
 * Merges translation dictionaries into a new object; a later source overrides an earlier one key by key.
 *
 * Nested namespaces are merged recursively, while texts and arrays of a later source replace the earlier value
 * instead of being concatenated, so a day-name list never grows by a second language. The keys `__proto__`,
 * `constructor` and `prototype` are skipped: `JSON.parse` keeps `__proto__` as an ordinary own key, and copying it
 * by assignment would replace the prototype of the merged namespace. Neither the sources nor their nested objects
 * are modified.
 *
 * @param sources - The dictionaries in ascending precedence; null and undefined entries are ignored
 * @returns A new dictionary holding the merged texts
 */
export function mergeTranslations(...sources: (TranslationDictionary | null | undefined)[]): TranslationDictionary {
  const target: TranslationDictionary = {};
  for (const source of sources) {
    if (source != null) {
      mergeInto(target, source);
    }
  }
  return target;
}

/**
 * Copies the own enumerable keys of a source into the target, recursing into namespaces present on both sides.
 *
 * @param target - The merged dictionary being built; it holds only objects created by this merge
 * @param source - The dictionary whose values take precedence
 * @private
 */
function mergeInto(target: TranslationDictionary, source: TranslationDictionary): void {
  for (const key of Object.keys(source)) {
    if (key === '__proto__' || key === 'constructor' || key === 'prototype') {
      continue;
    }
    const value = source[key];
    const existing = Object.prototype.hasOwnProperty.call(target, key) ? target[key] : undefined;
    if (isNamespace(value)) {
      const nested: TranslationDictionary = isNamespace(existing) ? existing : {};
      mergeInto(nested, value);
      target[key] = nested;
    } else {
      target[key] = Array.isArray(value) ? [...value] : value;
    }
  }
}

/**
 * Tells whether a value is a nested namespace, i.e. a plain object rather than a text, an array or null.
 *
 * @param value - The dictionary value to inspect
 * @returns true for a plain object
 * @private
 */
function isNamespace(value: unknown): value is TranslationDictionary {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
