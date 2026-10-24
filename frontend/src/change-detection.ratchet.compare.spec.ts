import { describe, expect, it } from 'vitest';
import { compareWithAllowlist, EAGER_STRATEGY, parseAllowlist } from './change-detection.ratchet';

describe('change detection ratchet comparison', () => {
  const existing = new Set(['a.ts', 'b.ts', 'c.ts']);

  it('accepts a tree whose Eager files are exactly the allowlist', () => {
    const result = compareWithAllowlist(['a.ts', 'b.ts'], ['a.ts', 'b.ts'], existing);
    expect(result).toEqual({ unlisted: [], missing: [], stale: [], duplicates: [] });
  });

  it('reports an Eager file that is not on the allowlist', () => {
    expect(compareWithAllowlist(['a.ts', 'c.ts'], ['a.ts'], existing).unlisted).toEqual(['c.ts']);
  });

  it('reports an entry whose file was deleted or renamed', () => {
    expect(compareWithAllowlist(['a.ts'], ['a.ts', 'gone.ts'], existing).missing).toEqual(['gone.ts']);
  });

  it('reports an entry whose file was converted but not removed from the list', () => {
    const result = compareWithAllowlist(['a.ts'], ['a.ts', 'b.ts'], existing);
    expect(result.stale).toEqual(['b.ts']);
    expect(result.missing).toEqual([]);
  });

  it('reports a duplicated entry', () => {
    expect(compareWithAllowlist(['a.ts'], ['a.ts', 'a.ts'], existing).duplicates).toEqual(['a.ts']);
  });

  it('parses the allowlist ignoring blank lines, comments and Windows separators', () => {
    expect(parseAllowlist('# header\r\nsrc\\app\\a.ts\r\n\r\n  src/app/b.ts  \n')).toEqual([
      'src/app/a.ts',
      'src/app/b.ts'
    ]);
  });

  it('matches Eager and the deprecated Default, not OnPush', () => {
    expect(EAGER_STRATEGY.test('changeDetection: ChangeDetectionStrategy.Eager,')).toBe(true);
    expect(EAGER_STRATEGY.test('changeDetection: ChangeDetectionStrategy.Default')).toBe(true);
    expect(EAGER_STRATEGY.test('changeDetection: ChangeDetectionStrategy.OnPush')).toBe(false);
  });
});
