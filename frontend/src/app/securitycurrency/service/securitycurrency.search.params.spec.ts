import { describe, expect, it } from 'vitest';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { AdvancedSearchOperator } from '../../shared/types/advanced.search.operator';
import { securitycurrencySearchParams } from './securitycurrency.search.params';

/**
 * The criteria of the advanced search travel as indexed query parameters. A list of objects must never reach the
 * generic builder, which would write `[object Object]`, and an empty list must add nothing.
 */
describe('securitycurrencySearchParams', () => {
  it('sends a search without criteria exactly as before', () => {
    const search = new SecuritycurrencySearch();
    search.name = 'Gold';
    search.leverageFactor = 0;

    const params = securitycurrencySearchParams(search, ['onlyTenantPrivate']);

    expect(params.keys().sort()).toEqual(['leverageFactor', 'name', 'onlyTenantPrivate']);
    expect(params.get('leverageFactor')).toBe('0');
    expect(params.has('advancedCriteria')).toBe(false);
  });

  it('writes every set property of every criterion as an indexed parameter, zero and false included', () => {
    const search = new SecuritycurrencySearch();
    search.advancedCriteria = [
      { field: 'U12', operator: AdvancedSearchOperator.BETWEEN, number: 0, numberTo: 20.5 },
      { field: 'U13', flag: false },
      { field: 'U14', operator: AdvancedSearchOperator.GREATER_EQUAL, date: '20260315', dateTo: null, text: '' }
    ];

    const params = securitycurrencySearchParams(search);

    expect(params.get('advancedCriteria[0].field')).toBe('U12');
    expect(params.get('advancedCriteria[0].operator')).toBe('BETWEEN');
    expect(params.get('advancedCriteria[0].number')).toBe('0');
    expect(params.get('advancedCriteria[0].numberTo')).toBe('20.5');
    expect(params.get('advancedCriteria[1].flag')).toBe('false');
    expect(params.get('advancedCriteria[2].date')).toBe('20260315');
    expect(params.has('advancedCriteria[2].dateTo')).toBe(false);
    expect(params.has('advancedCriteria[2].text')).toBe(false);
    expect(params.keys().some((key) => key === 'advancedCriteria')).toBe(false);
    expect(params.toString()).not.toContain('object');
  });
});
