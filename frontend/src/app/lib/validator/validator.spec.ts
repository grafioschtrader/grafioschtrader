import { describe, it, expect } from 'vitest';
import { FormControl, FormGroup } from '@angular/forms';
import { isPresent, gtWithMask, gteWithMask, webUrl, dateRange } from './validator';

describe('isPresent', () => {
  it('returns true for string value', () => {
    expect(isPresent('hello')).toBe(true);
  });

  it('returns true for zero', () => {
    expect(isPresent(0)).toBe(true);
  });

  it('returns true for empty string', () => {
    expect(isPresent('')).toBe(true);
  });

  it('returns false for null', () => {
    expect(isPresent(null)).toBe(false);
  });

  it('returns false for undefined', () => {
    expect(isPresent(undefined)).toBe(false);
  });
});

describe('gtWithMask', () => {
  it('returns null when value is greater than threshold', () => {
    const validator = gtWithMask(5);
    expect(validator(new FormControl(10))).toBeNull();
  });

  it('returns error when value equals threshold', () => {
    const validator = gtWithMask(5);
    const result = validator(new FormControl(5));
    expect(result).toEqual({ gt: { requiredGt: 5, actualValue: 5 } });
  });

  it('returns error when value is less than threshold', () => {
    const validator = gtWithMask(5);
    const result = validator(new FormControl(3));
    expect(result).toEqual({ gt: { requiredGt: 5, actualValue: 3 } });
  });

  it('handles string value with mask characters', () => {
    const validator = gtWithMask(0);
    expect(validator(new FormControl('$1,000'))).toBeNull();
  });

  it('returns null for empty control (required not met)', () => {
    const validator = gtWithMask(5);
    expect(validator(new FormControl(''))).toBeNull();
  });
});

describe('gteWithMask', () => {
  it('returns null when value equals threshold', () => {
    const validator = gteWithMask(5);
    expect(validator(new FormControl(5))).toBeNull();
  });

  it('returns null when value is greater than threshold', () => {
    const validator = gteWithMask(5);
    expect(validator(new FormControl(10))).toBeNull();
  });

  it('returns error when value is less than threshold', () => {
    const validator = gteWithMask(5);
    const result = validator(new FormControl(3));
    expect(result).toEqual({ gte: { requiredGt: 5, actualValue: 3 } });
  });

  it('handles string value with mask characters', () => {
    const validator = gteWithMask(100);
    expect(validator(new FormControl('$150.00'))).toBeNull();
  });
});

describe('webUrl', () => {
  it('accepts valid https URL', () => {
    expect(webUrl(new FormControl('https://example.com'))).toBeNull();
  });

  it('accepts valid http URL with path', () => {
    expect(webUrl(new FormControl('http://example.com/path?q=1'))).toBeNull();
  });

  it('accepts URL with port', () => {
    expect(webUrl(new FormControl('https://example.com:8080/api'))).toBeNull();
  });

  it('rejects plain text', () => {
    expect(webUrl(new FormControl('not a url'))).toEqual({ webUrl: true });
  });

  it('rejects URL without protocol', () => {
    expect(webUrl(new FormControl('example.com'))).toEqual({ webUrl: true });
  });

  it('returns null for empty control', () => {
    expect(webUrl(new FormControl(''))).toBeNull();
  });
});

describe('dateRange', () => {
  it('returns null when date1 <= date2', () => {
    const group = new FormGroup({
      from: new FormControl(new Date(2024, 0, 1)),
      to: new FormControl(new Date(2024, 5, 1)),
    });
    const validator = dateRange('from', 'to', 'from');
    expect(validator(group)).toBeNull();
  });

  it('returns error when date1 > date2', () => {
    const group = new FormGroup({
      from: new FormControl(new Date(2024, 5, 1)),
      to: new FormControl(new Date(2024, 0, 1)),
    });
    const validator = dateRange('from', 'to', 'from');
    expect(validator(group)).toHaveProperty('dateRange');
  });

  it('returns null when date1 equals date2', () => {
    const d = new Date(2024, 3, 15);
    const group = new FormGroup({
      from: new FormControl(d),
      to: new FormControl(new Date(d.getTime())),
    });
    const validator = dateRange('from', 'to', 'from');
    expect(validator(group)).toBeNull();
  });

  it('returns null when either date is null', () => {
    const group = new FormGroup({
      from: new FormControl(null),
      to: new FormControl(new Date(2024, 0, 1)),
    });
    const validator = dateRange('from', 'to', 'from');
    expect(validator(group)).toBeNull();
  });
});
