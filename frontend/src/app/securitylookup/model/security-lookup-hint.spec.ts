import { describe, expect, it } from 'vitest';

import { hintParams } from './security-lookup-hint';

describe('hintParams', () => {
  const translate = (key: string) => `t(${key})`;
  const formatDate = (iso: string) => iso.split('-').reverse().join('.');

  it('translates Key arguments and formats Date arguments under the name without suffix', () => {
    expect(
      hintParams(
        { provider: 'six', statusKey: 'TIMEOUT', defaultDate: '2000-01-03', otherKey: 'DF_YEAR' },
        translate,
        formatDate
      )
    ).toEqual({ provider: 'six', status: 't(TIMEOUT)', default: '03.01.2000', other: 't(DF_YEAR)' });
  });

  it('accepts a hint without arguments', () => {
    expect(hintParams(undefined, translate, formatDate)).toEqual({});
  });
});
