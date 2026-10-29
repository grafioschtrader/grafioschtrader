import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { SelectOptionsHelper } from './select.options.helper';
import { ValueKeyHtmlSelectOptions } from '../dynamic-form/models/value.key.html.select.options';

enum Choice {
  First = 1,
  Second = 2,
  Third = 3
}

describe('enum option filters', () => {
  const translator = { get: (key: string) => of(key) } as unknown as TranslateService;

  it('distinguishes an absent filter from an explicitly empty allow-list', () => {
    expect(SelectOptionsHelper.createHtmlOptionsFromEnum(translator, Choice, null).map((option) => option.key)).toEqual(
      ['First', 'Second', 'Third']
    );
    expect(SelectOptionsHelper.createHtmlOptionsFromEnum(translator, Choice, []).map((option) => option.key)).toEqual(
      []
    );
  });

  it('supports numeric allow-lists and named deny-lists', () => {
    expect(
      SelectOptionsHelper.createHtmlOptionsFromEnum(translator, Choice, [Choice.Second]).map((option) => option.key)
    ).toEqual(['Second']);
    expect(
      SelectOptionsHelper.createHtmlOptionsFromEnum(translator, Choice, ['Second'], true).map((option) => option.key)
    ).toEqual(['First', 'Third']);
  });

  it('retains a clear selection while disabling a named option at numeric key zero', () => {
    const options = [new ValueKeyHtmlSelectOptions(null, 'Unassigned'), new ValueKeyHtmlSelectOptions(0, 'Zero')];
    SelectOptionsHelper.disableEnableExistingHtmlOptionsFromEnum(options, { Zero: 0, 0: 'Zero' }, ['Zero']);
    expect(options.map((option) => option.disabled)).toEqual([false, true]);
    const grouped = SelectOptionsHelper.createGroupItemsFromValueKeyHtmlSelectOptions(options);
    expect(grouped.map((option) => option.key)).toEqual([null, 0]);
  });
});
