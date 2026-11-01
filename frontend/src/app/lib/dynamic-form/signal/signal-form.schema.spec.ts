import { FormControl, FormGroup, Validators } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { numberRange } from '../../validator/validator';
import { DataType } from '../models/data.type';
import { FieldConfig } from '../models/field.config';
import { FormGroupDefinition } from '../models/form.group.definition';
import { InputType } from '../models/input.type';
import {
  fromModelValue,
  initialSignalFormModel,
  signalFieldEntries,
  toModelValue,
  validatorErrors
} from './signal-form.schema';

const field = (name: string, inputType: InputType, defaultValue?: unknown): FieldConfig => ({
  field: name,
  inputType,
  dataType: DataType.String,
  defaultValue
});

describe('signal form schema', () => {
  it('builds a defined model value for every data field, nested by group, without buttons', () => {
    const range: FormGroupDefinition = {
      formGroupName: 'range',
      fieldConfig: [field('lower', InputType.InputNumber), field('upper', InputType.InputNumber, 10)]
    };
    const config = [
      field('name', InputType.Input),
      field('active', InputType.Checkbox),
      field('currency', InputType.Select, 'CHF'),
      range,
      field('submit', InputType.Pbutton)
    ];

    const entries = signalFieldEntries(config);

    expect(entries.map((entry) => entry.config.field)).toEqual(['name', 'active', 'currency', 'lower', 'upper']);
    expect(entries.find((entry) => entry.config.field === 'upper')?.group).toBe(range);
    // undefined would mean a missing field to signal forms, and a null text input is parsed as a number
    expect(initialSignalFormModel(entries)).toEqual({
      name: '',
      active: false,
      currency: 'CHF',
      range: { lower: null, upper: 10 }
    });
  });

  it('reports the errors of reactive validator functions under their names, which the error rules use', () => {
    expect(validatorErrors(Validators.pattern('[A-Z]{3}'), new FormControl('chf'))).toEqual([{ kind: 'pattern' }]);
    expect(validatorErrors(Validators.pattern('[A-Z]{3}'), new FormControl('CHF'))).toEqual([]);

    const group = (lower: number | null, upper: number | null) =>
      new FormGroup({ lower: new FormControl(lower), upper: new FormControl(upper) });
    expect(validatorErrors(numberRange('lower', 'upper'), group(5, 3))).toEqual([{ kind: 'numberRange' }]);
    expect(validatorErrors(numberRange('lower', 'upper'), group(3, 5))).toEqual([]);
    expect(validatorErrors(numberRange('lower', 'upper'), group(null, 5))).toEqual([]);
  });

  it('transfers an untouched empty text input as null and a cleared one as empty string, as reactive forms do', () => {
    const text = field('name', InputType.Input);
    const select = field('currency', InputType.Select);
    expect(toModelValue(text, null)).toBe('');
    expect(toModelValue(select, undefined)).toBeNull();
    expect(fromModelValue(text, '', false)).toBeNull();
    expect(fromModelValue(text, '', true)).toBe('');
    expect(fromModelValue(select, '', false)).toBe('');
  });

  it('starts a native time input as a string so its first entry cannot become an ISO date', () => {
    const time = { ...field('timeOpen', InputType.Input), dataType: DataType.TimeString };
    expect(toModelValue(time, null)).toBe('');
    expect(toModelValue(time, undefined)).toBe('');
    expect(toModelValue(time, '09:00:00')).toBe('09:00:00');
    expect(fromModelValue(time, '', false)).toBeNull();
    expect(fromModelValue(time, '09:00:00', true)).toBe('09:00:00');
  });
});
