import { afterEach, describe, expect, it } from 'vitest';
import { mergeTranslations } from './merge.translations';

describe('mergeTranslations', () => {
  afterEach(() => {
    delete (Object.prototype as Record<string, unknown>)['polluted'];
  });

  it('lets a later source override texts and merges nested namespaces', () => {
    const backend = { SAVE: 'Save', optimus: { today: 'Today (server)', clear: 'Clear' } };
    const optimus = { optimus: { today: 'Today', dayNames: ['Sunday', 'Monday'] } };

    expect(mergeTranslations(backend, optimus)).toEqual({
      SAVE: 'Save',
      optimus: { today: 'Today', clear: 'Clear', dayNames: ['Sunday', 'Monday'] }
    });
  });

  it('replaces an array instead of concatenating it', () => {
    const merged = mergeTranslations({ dayNames: ['Sun'] }, { dayNames: ['So'] });

    expect(merged['dayNames']).toEqual(['So']);
  });

  it('leaves its sources unchanged', () => {
    const first = { GT_FILTER: { gtIS: 'is' } };
    const second = { GT_FILTER: { gtNOT: 'not' } };

    mergeTranslations(first, second);

    expect(first).toEqual({ GT_FILTER: { gtIS: 'is' } });
    expect(second).toEqual({ GT_FILTER: { gtNOT: 'not' } });
  });

  it('ignores __proto__, constructor and prototype keys of parsed JSON', () => {
    const hostile = JSON.parse(
      '{"__proto__": {"polluted": "yes"}, "constructor": {"prototype": {"polluted": "yes"}}, ' +
        '"ns": {"__proto__": {"polluted": "yes"}}, "OK": "ok"}'
    ) as Record<string, unknown>;

    const merged = mergeTranslations({ ns: { A: 'a' } }, hostile);

    expect(merged).toEqual({ ns: { A: 'a' }, OK: 'ok' });
    expect(Object.getPrototypeOf(merged)).toBe(Object.prototype);
    expect(Object.getPrototypeOf(merged['ns'])).toBe(Object.prototype);
    expect(({} as Record<string, unknown>)['polluted']).toBeUndefined();
  });
});
