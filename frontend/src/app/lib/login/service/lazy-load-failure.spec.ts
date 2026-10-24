import { describe, expect, it } from 'vitest';
import { isChunkLoadFailure } from './lazy-load-failure';

describe('isChunkLoadFailure', () => {
  it('recognises the failed dynamic import of Chrome, Firefox and Safari', () => {
    expect(
      isChunkLoadFailure(new TypeError('Failed to fetch dynamically imported module: https://x/chunk-AB12.js'))
    ).toBe(true);
    expect(isChunkLoadFailure(new TypeError('error loading dynamically imported module: https://x/c.js'))).toBe(true);
    expect(isChunkLoadFailure(new TypeError('Importing a module script failed.'))).toBe(true);
  });

  it('leaves other navigation errors alone', () => {
    expect(isChunkLoadFailure(new Error('NG04002: Cannot match any routes'))).toBe(false);
    expect(isChunkLoadFailure(null)).toBe(false);
  });
});
