import { describe, expect, it } from 'vitest';
import { FieldConfig } from './field.config';
import { FormGroupDefinition } from './form.group.definition';
import { FormNotifier } from './form.notifier';
import { InputType } from './input.type';
import { DataType } from './data.type';

function field<T extends Partial<FieldConfig>>(
  name: string,
  extra: T
): FieldConfig & Required<Pick<FieldConfig, Extract<keyof T, keyof FieldConfig>>>;
function field(name: string): FieldConfig;
function field(name: string, extra: Partial<FieldConfig> = {}): FieldConfig {
  return { field: name, inputType: InputType.Input, dataType: DataType.String, ...extra };
}

describe('FormNotifier.instrument', () => {
  it('notifies when a display property changes and not when the same value is assigned again', () => {
    const name = field('name', { invisible: false });
    const notifier = FormNotifier.instrument([name]);
    const start = notifier.revision();
    name.invisible = true;
    expect(notifier.revision()).toBe(start + 1);
    name.invisible = true;
    expect(notifier.revision()).toBe(start + 1);
    expect(name.invisible).toBe(true);
  });

  it('notifies on the first assignment of a property absent at instrumentation time', () => {
    const name = field('name');
    const notifier = FormNotifier.instrument([name]);
    const start = notifier.revision();
    name.placeholder = 'hint';
    expect(notifier.revision()).toBe(start + 1);
  });

  it('does not notify for properties that are no display state', () => {
    const name = field('name');
    const notifier = FormNotifier.instrument([name]);
    const start = notifier.revision();
    name.defaultValue = 'x';
    name.dataproperty = 'a.b';
    expect(notifier.revision()).toBe(start);
  });

  it('notifies for a new array but not for an element pushed into the existing one', () => {
    const select = field('select', { valueKeyHtmlOptions: [] });
    const notifier = FormNotifier.instrument([select]);
    const start = notifier.revision();
    select.valueKeyHtmlOptions.push({ key: 1, value: 'one' } as any);
    expect(notifier.revision()).toBe(start);
    select.valueKeyHtmlOptions = [...select.valueKeyHtmlOptions];
    expect(notifier.revision()).toBe(start + 1);
  });

  it('notifies for changes inside the nested settings, also of a settings object assigned later', () => {
    const amount = field('amount', { inputNumberSettings: { maxFractionDigits: 2 } });
    const notifier = FormNotifier.instrument([amount]);
    let expected = notifier.revision();
    amount.inputNumberSettings.currency = 'CHF';
    expect(notifier.revision()).toBe(++expected);
    amount.calendarConfig = { minDate: new Date(2020, 0, 1) };
    expect(notifier.revision()).toBe(++expected);
    amount.calendarConfig.maxDate = new Date(2030, 0, 1);
    expect(notifier.revision()).toBe(++expected);
  });

  it('instruments the fields of a form group and the group itself', () => {
    const from = field('from');
    const to = field('to');
    const group: FormGroupDefinition = { formGroupName: 'range', fieldConfig: [from, to] };
    const notifier = FormNotifier.instrument([field('top'), group]);
    let expected = notifier.revision();
    to.disabled = true;
    expect(notifier.revision()).toBe(++expected);
    group.errors = [];
    expect(notifier.revision()).toBe(++expected);
    expect(FormNotifier.of(from)).toBe(notifier);
    expect(FormNotifier.of(group)).toBe(notifier);
  });

  it('is idempotent: repeated calls keep one notifier and notify once per change', () => {
    const name = field('name');
    const config = [name];
    const first = FormNotifier.instrument(config);
    const second = FormNotifier.instrument(config, new FormNotifier());
    expect(second).toBe(first);
    const start = first.revision();
    name.labelKey = 'NAME';
    expect(first.revision()).toBe(start + 1);
  });

  it('reuses the notifier of an already instrumented field for a config filled later or replaced', () => {
    const name = field('name');
    const notifier = FormNotifier.instrument([name]);
    const added = field('added');
    expect(FormNotifier.instrument([name, added])).toBe(notifier);
    const start = notifier.revision();
    added.readonly = true;
    expect(notifier.revision()).toBe(start + 1);
  });

  it('uses the passed notifier for a config never instrumented before', () => {
    const own = new FormNotifier();
    expect(FormNotifier.instrument([field('name')], own)).toBe(own);
    expect(FormNotifier.instrument([], own)).toBe(own);
  });

  it('moves every object of a merged config onto one notifier', () => {
    const a = field('a');
    const b = field('b');
    const notifierA = FormNotifier.instrument([a]);
    FormNotifier.instrument([b]);
    const merged = FormNotifier.instrument([a, b]);
    expect(merged).toBe(notifierA);
    const start = notifierA.revision();
    b.invisible = true;
    expect(notifierA.revision()).toBe(start + 1);
  });

  it('keeps the objects serializable, spreadable and enumerable as plain data', () => {
    const name = field('name', { maxLength: 20 });
    FormNotifier.instrument([name]);
    expect(JSON.parse(JSON.stringify(name))).toEqual({
      field: 'name',
      inputType: InputType.Input,
      dataType: DataType.String,
      maxLength: 20
    });
    const copy = { ...name };
    expect(copy.maxLength).toBe(20);
    expect(FormNotifier.of(copy)).toBeUndefined();
    expect(Object.keys(name)).toContain('maxLength');
  });
});
