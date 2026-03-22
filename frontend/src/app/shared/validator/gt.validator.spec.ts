import { describe, it, expect } from 'vitest';
import { FormControl } from '@angular/forms';
import { validISIN } from './gt.validator';

describe('validISIN', () => {
  it('accepts valid ISIN US0378331005 (Apple)', () => {
    const control = new FormControl('US0378331005');
    expect(validISIN(control)).toBeNull();
  });

  it('accepts valid ISIN CH0012032048 (Roche)', () => {
    const control = new FormControl('CH0012032048');
    expect(validISIN(control)).toBeNull();
  });

  it('accepts valid ISIN DE0007164600 (SAP)', () => {
    const control = new FormControl('DE0007164600');
    expect(validISIN(control)).toBeNull();
  });

  it('accepts valid ISIN GB0002634946 (BAE Systems)', () => {
    const control = new FormControl('GB0002634946');
    expect(validISIN(control)).toBeNull();
  });

  it('rejects ISIN with bad check digit', () => {
    const control = new FormControl('US0378331009');
    expect(validISIN(control)).toEqual({ validISIN: true });
  });

  it('rejects ISIN with invalid country code', () => {
    const control = new FormControl('XX0378331005');
    expect(validISIN(control)).toEqual({ validISIN: true });
  });

  it('rejects short string', () => {
    const control = new FormControl('US037833');
    expect(validISIN(control)).toEqual({ validISIN: true });
  });

  it('returns null for empty control (passes through to required)', () => {
    const control = new FormControl('');
    expect(validISIN(control)).toBeNull();
  });

  it('returns null for null control value', () => {
    const control = new FormControl(null);
    expect(validISIN(control)).toBeNull();
  });
});
