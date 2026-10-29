/** Class sources accepted by the reusable table containers. */
export type CssClasses = string | readonly string[] | Readonly<Record<string, boolean>>;

/** Combines class sources into a string, preserving space-separated names in arrays and object keys. */
export function toClassString(...sources: (CssClasses | null | undefined)[]): string {
  return sources
    .flatMap((source) => {
      if (!source) {
        return [];
      }
      if (typeof source === 'string') {
        return [source];
      }
      if (Array.isArray(source)) {
        return source;
      }
      return Object.keys(source).filter((key) => (source as Readonly<Record<string, boolean>>)[key]);
    })
    .join(' ');
}
