import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { InputRule } from './input-rule';

describe('parameter input rules', () => {
  it('keeps an absent rule distinct from a numeric rule starting at zero', () => {
    for (const input of [null, undefined, '', '  ']) {
      expect(InputRule.parse(input)).toBeNull();
    }
    const rule = InputRule.parse('min:0,max:2');
    if (!rule) throw new Error('Expected a parsed rule');
    const control = new FormControl<number | null>(null, rule.getValidators());
    expect(control.valid).toBe(true);
    control.setValue(0);
    expect(control.valid).toBe(true);
    control.setValue(-1);
    expect(control.hasError('range')).toBe(true);
    control.setValue(3);
    expect(control.hasError('range')).toBe(true);
  });

  it('allows optional enum values to be cleared without interpreting them as zero', () => {
    const rule = InputRule.parse('enum:0,7,12');
    if (!rule) throw new Error('Expected a parsed rule');
    const control = new FormControl<string | number | null>(null, rule.getValidators());
    for (const value of [null, '', 0, '7']) {
      control.setValue(value);
      expect(control.valid).toBe(true);
    }
    control.setValue(1);
    expect(control.errors).toEqual({ enum: { allowedValues: [0, 7, 12], actualValue: 1 } });
  });
});
