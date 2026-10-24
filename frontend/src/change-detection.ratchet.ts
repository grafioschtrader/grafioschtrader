/**
 * Comparison logic of the change detection ratchet (see change-detection.ratchet.spec.ts).
 *
 * Angular 22 makes OnPush the component default. A component that still declares ChangeDetectionStrategy.Eager (or
 * the deprecated alias Default) relies on zone.js re-checking it on every pass. The ratchet allows that only for the
 * files listed in eager-allowlist.txt, a list that can only shrink: every conversion removes its line in the same
 * commit, and a line whose file no longer declares Eager is reported as stale.
 */

/** Matches an explicit declaration of the strategies a new component must not use. */
export const EAGER_STRATEGY = /ChangeDetectionStrategy\.(Eager|Default)\b/;

/** Outcome of comparing the files that declare Eager with the allowlist. */
export interface RatchetResult {
  /** Files declaring Eager or Default that are not on the allowlist */
  unlisted: string[];
  /** Allowlist entries whose file does not exist any more */
  missing: string[];
  /** Allowlist entries whose file exists but no longer declares Eager */
  stale: string[];
  /** Allowlist entries that occur more than once */
  duplicates: string[];
}

/**
 * Parses the allowlist text: one repository-relative path per line, blank lines and lines starting with '#' ignored.
 *
 * @param text - Content of eager-allowlist.txt
 * @returns The listed paths in file order, normalized to forward slashes
 */
export function parseAllowlist(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('#'))
    .map((line) => line.replace(/\\/g, '/'));
}

/**
 * Compares the files that declare Eager with the allowlist.
 *
 * @param eagerFiles - Repository-relative paths of the source files that declare Eager or Default
 * @param allowlist - Entries of eager-allowlist.txt
 * @param existingFiles - Repository-relative paths of all scanned source files
 * @returns Every violation, each list sorted; all lists empty when the tree conforms
 */
export function compareWithAllowlist(
  eagerFiles: readonly string[],
  allowlist: readonly string[],
  existingFiles: ReadonlySet<string>
): RatchetResult {
  const allowed = new Set(allowlist);
  const eager = new Set(eagerFiles);
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const entry of allowlist) {
    if (seen.has(entry)) {
      duplicates.add(entry);
    }
    seen.add(entry);
  }
  return {
    unlisted: [...eager].filter((file) => !allowed.has(file)).sort(),
    missing: [...allowed].filter((entry) => !existingFiles.has(entry)).sort(),
    stale: [...allowed].filter((entry) => existingFiles.has(entry) && !eager.has(entry)).sort(),
    duplicates: [...duplicates].sort()
  };
}
