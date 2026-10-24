import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { compareWithAllowlist, EAGER_STRATEGY, parseAllowlist } from './change-detection.ratchet';

/**
 * Keeps ChangeDetectionStrategy.Eager from spreading. A new component declares no changeDetection and so runs as
 * OnPush; the files that still need Eager are listed in eager-allowlist.txt, which only ever shrinks. See
 * frontend/CLAUDE.md → "Change detection".
 */

/** This file lives in `frontend/src`, so the repository root is two levels up. */
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const SRC = path.join(REPO_ROOT, 'frontend/src');
const ALLOWLIST = path.join(SRC, 'eager-allowlist.txt');

/** Collects every TypeScript source below `src`, spec files included, as repository-relative paths. */
function collectSources(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      collectSources(full, out);
    } else if (entry.name.endsWith('.ts') && full !== fileURLToPath(import.meta.url)) {
      out.push(path.relative(REPO_ROOT, full).replace(/\\/g, '/'));
    }
  }
  return out;
}

describe('change detection ratchet', () => {
  const sources = collectSources(SRC).filter((file) => !file.startsWith('frontend/src/change-detection.ratchet'));
  const eagerFiles = sources.filter((file) => EAGER_STRATEGY.test(fs.readFileSync(path.join(REPO_ROOT, file), 'utf8')));
  const result = compareWithAllowlist(eagerFiles, parseAllowlist(fs.readFileSync(ALLOWLIST, 'utf8')), new Set(sources));

  it('allows ChangeDetectionStrategy.Eager only in files on the allowlist', () => {
    expect(
      result.unlisted,
      'These files declare ChangeDetectionStrategy.Eager or Default. A new component declares no changeDetection ' +
        '(OnPush is the default) and notifies through signals; see frontend/CLAUDE.md → "Change detection".'
    ).toEqual([]);
  });

  it('lists only existing files that still declare Eager', () => {
    expect(result.missing, 'Allowlist entries whose file no longer exists; remove the lines.').toEqual([]);
    expect(result.stale, 'Allowlist entries whose file no longer declares Eager; remove the lines.').toEqual([]);
    expect(result.duplicates, 'Allowlist entries listed more than once.').toEqual([]);
  });
});
