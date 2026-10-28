import { describe, expect, it } from 'vitest';
import { toClassString } from './css-class.helper';

describe('table container classes', () => {
  it('preserves conditional classes and additional nested-table classes together', () => {
    expect(
      toClassString({ 'data-container': true, 'active-border': false, 'passiv-border': true }, 'datatable nestedtable')
    ).toBe('data-container passiv-border datatable nestedtable');
  });

  it('preserves space-separated class names in object keys and array entries', () => {
    expect(toClassString({ 'fa fa-check': true, 'fa fa-times': false }, ['small-padding col-md-12', 'text-end'])).toBe(
      'fa fa-check small-padding col-md-12 text-end'
    );
  });

  it('handles absent classes and re-evaluates conditional objects without changing them', () => {
    const classes = { 'active-border': false };
    expect(toClassString(null, undefined, '', classes)).toBe('');
    classes['active-border'] = true;
    expect(toClassString(classes)).toBe('active-border');
    expect(classes).toEqual({ 'active-border': true });
  });
});
