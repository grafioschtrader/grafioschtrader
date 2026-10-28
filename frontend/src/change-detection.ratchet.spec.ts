import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Forbids ChangeDetectionStrategy.Eager and its deprecated alias Default. Both hosts run zoneless, so a component is
 * refreshed only when a signal, an input, an AsyncPipe or markForCheck notifies Angular - which is what OnPush, the
 * Angular 22 default, already demands. A new component declares no changeDetection; see frontend/CLAUDE.md →
 * "Change detection".
 */
const EAGER_STRATEGY = /ChangeDetectionStrategy\.(Eager|Default)\b/;

/** This file lives in `frontend/src`, so the repository root is two levels up. */
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const SRC = path.join(REPO_ROOT, 'frontend/src');

/** Collects every TypeScript source below `src` except this file, as repository-relative paths. */
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
  it('matches Eager and the deprecated Default, not OnPush', () => {
    expect(EAGER_STRATEGY.test('changeDetection: ChangeDetectionStrategy.Eager,')).toBe(true);
    expect(EAGER_STRATEGY.test('changeDetection: ChangeDetectionStrategy.Default')).toBe(true);
    expect(EAGER_STRATEGY.test('changeDetection: ChangeDetectionStrategy.OnPush')).toBe(false);
  });

  it('finds no source declaring ChangeDetectionStrategy.Eager or Default', () => {
    const eagerFiles = collectSources(SRC).filter((file) =>
      EAGER_STRATEGY.test(fs.readFileSync(path.join(REPO_ROOT, file), 'utf8'))
    );
    expect(
      eagerFiles,
      'These files declare ChangeDetectionStrategy.Eager or Default. A new component declares no changeDetection ' +
        '(OnPush is the default) and notifies through signals; see frontend/CLAUDE.md → "Change detection".'
    ).toEqual([]);
  });
});
