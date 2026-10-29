import { TranslateService } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { firstValueFrom, Subject } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { TranslateHelper } from './translate.helper';

describe('menu translation without a selection', () => {
  const unusedTranslator = {
    get: () => {
      throw new Error('An absent or empty menu needs no translation.');
    }
  } as unknown as TranslateService;

  it('preserves an absent menu so its top-level section can be disabled', async () => {
    expect(await firstValueFrom(TranslateHelper.translateMenuItems$(null, unusedTranslator))).toBeNull();
    expect(await firstValueFrom(TranslateHelper.translateMenuItems$(undefined, unusedTranslator))).toBeNull();
  });

  it('emits an empty array for a present menu without entries', async () => {
    const menu: MenuItem[] = [];
    const translated = await firstValueFrom(TranslateHelper.translateMenuItems$(menu, unusedTranslator));
    expect(translated).toEqual([]);
    expect(translated).not.toBe(menu);
  });

  it('publishes a separate menu only after child labels and tooltips have been translated', () => {
    const texts = new Map(['PARENT', 'CHILD', 'CHILD_TITLE'].map((key) => [key, new Subject<string>()]));
    const translator = { get: (key: string) => texts.get(key) } as unknown as TranslateService;
    const command = () => {};
    const menu: MenuItem[] = [
      { label: 'PARENT', command, items: [{ label: 'CHILD', tooltipOptions: { tooltipLabel: 'CHILD_TITLE' } }] }
    ];
    const emissions: MenuItem[][] = [];
    TranslateHelper.translateMenuItems$(menu, translator).subscribe((items) => emissions.push(items));

    const resolve = (key: string): void => {
      const text = texts.get(key);
      if (!text) throw new Error(`Unexpected translation key: ${key}`);
      text.next(key.toLowerCase());
      text.complete();
    };
    resolve('PARENT');
    resolve('CHILD');
    expect(emissions).toEqual([]);
    resolve('CHILD_TITLE');

    expect(emissions).toEqual([
      [{ label: 'parent', command, items: [{ label: 'child', tooltipOptions: { tooltipLabel: 'child_title' } }] }]
    ]);
    expect(menu[0].label).toBe('PARENT');
    expect(menu[0].items).toEqual([{ label: 'CHILD', tooltipOptions: { tooltipLabel: 'CHILD_TITLE' } }]);
  });
});
