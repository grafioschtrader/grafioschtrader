import { FormControl, FormGroup, Validators } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { requireFormControl } from './form-control-access';

describe('required form control lifecycle', () => {
  it('rejects an unbuilt field, then uses its real control without replacing validation or zero values', () => {
    const field: { field: string; formControl?: FormControl<number | null> } = { field: 'amount' };
    expect(() => requireFormControl(field)).toThrow('Form control "amount" is not initialized');
    expect(field.formControl).toBeUndefined();

    field.formControl = new FormControl<number | null>(null, Validators.required);
    const form = new FormGroup({ amount: field.formControl });
    expect(form.valid).toBe(false);
    requireFormControl(field).setValue(0);
    expect(form.valid).toBe(true);
    expect(form.getRawValue()).toEqual({ amount: 0 });
    requireFormControl(field).reset();
    expect(form.valid).toBe(false);
    expect(form.getRawValue()).toEqual({ amount: null });
  });

  it('preserves group controls and accepts a disabled control as initialized', () => {
    const field = { field: 'note', formControl: new FormControl({ value: 'saved', disabled: true }) };
    const group = {
      formGroupName: 'details',
      formControl: new FormGroup<Record<string, FormControl<string | null>>>({ note: field.formControl })
    };
    requireFormControl(group).addControl('extra', new FormControl('additional'));
    expect(requireFormControl(group).getRawValue()).toEqual({ note: 'saved', extra: 'additional' });
    expect(requireFormControl(field).disabled).toBe(true);
    expect(() => requireFormControl({ formGroupName: 'not-built' })).toThrow('Form control "not-built"');
  });
});
