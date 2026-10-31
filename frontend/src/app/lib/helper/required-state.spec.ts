import { describe, expect, it } from 'vitest';
import { requireInitialized } from './required-state';
import { FormBase } from '../edit/form.base';
import type { FieldConfig } from '../dynamic-form/models/field.config';
import { InputType } from '../dynamic-form/models/input.type';
import { DataType } from '../dynamic-form/models/data.type';

class TestForm extends FormBase {
  previousConfig() {
    return this.configObjectIfInitialized;
  }
}

describe('required lifecycle state', () => {
  it('rejects absent state with its owner name and retains valid falsy values', () => {
    expect(() => requireInitialized(undefined, 'test form')).toThrow('test form has not been initialized');
    expect(() => requireInitialized(null, 'test form')).toThrow('test form has not been initialized');
    expect(requireInitialized(0, 'zero')).toBe(0);
    expect(requireInitialized(false, 'flag')).toBe(false);
    expect(requireInitialized('', 'text')).toBe('');
  });

  it('distinguishes an unbuilt form from a configured form and retains replacements during rebuilding', () => {
    const form = new TestForm();
    expect(form.previousConfig()).toBeUndefined();
    expect(() => form.configObject).toThrow('FormBase.configObject');
    expect(() => form.formConfig).toThrow('FormBase.formConfig');
    const name: FieldConfig = { inputType: InputType.Input, dataType: DataType.String, field: 'name' };
    form.formConfig = { labelColumns: 3, locale: 'de-CH' };
    form.configObject = { name };
    expect(form.previousConfig()?.name).toBe(name);
    expect(form.formConfig.labelColumns).toBe(3);
    const replacement = { name: { ...name, disabled: true } };
    form.configObject = replacement;
    expect(form.configObject).toBe(replacement);
    expect(form.previousConfig()?.name.disabled).toBe(true);
    expect(name.disabled).toBeUndefined();
  });
});
