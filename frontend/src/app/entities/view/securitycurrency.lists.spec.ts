import { describe, expect, it } from 'vitest';
import { SecuritycurrencyLists } from './securitycurrency.lists';
import { Currencypair } from '../currencypair';
import { Security } from '../security';

describe('SecuritycurrencyLists', () => {
  it('puts a plain currency-pair response in currencypairList', () => {
    const currencypair = { idSecuritycurrency: 1, fromCurrency: 'EUR', toCurrency: 'USD' } as Currencypair;

    const result = SecuritycurrencyLists.fromSecuritycurrency(currencypair);

    expect(result.currencypairList).toEqual([currencypair]);
    expect(result.securityList).toEqual([]);
  });

  it('puts a security response in securityList', () => {
    const security = { idSecuritycurrency: 2, name: 'Example' } as Security;

    const result = SecuritycurrencyLists.fromSecuritycurrency(security);

    expect(result.securityList).toEqual([security]);
    expect(result.currencypairList).toEqual([]);
  });
});
