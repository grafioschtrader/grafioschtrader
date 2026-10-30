import { describe, expect, it } from 'vitest';
import {
  MobileWatchlistGroup,
  mobileWatchlistRows,
  nextMobileWatchlistSort,
  sortMobileWatchlistRows
} from './mobile.watchlist.rows';

const group: MobileWatchlistGroup = {
  securityPositionList: [
    {
      securitycurrency: {
        idSecuritycurrency: 10,
        name: 'Nestlé N',
        currency: 'CHF',
        sLast: 84.12,
        sChangePercentage: 1.24,
        priceFractionDigits: null,
        assetClass: { categoryType: 'EQUITIES', specialInvestmentInstrument: 'DIRECT_INVESTMENT' }
      },
      units: 150,
      positionGainLossPercentage: 12.4,
      valueSecurity: 12618
    },
    {
      securitycurrency: {
        idSecuritycurrency: 11,
        name: 'Novartis N',
        currency: 'CHF',
        sLast: 88.5,
        sChangePercentage: -0.62,
        assetClass: { categoryType: 'EQUITIES', specialInvestmentInstrument: 'DIRECT_INVESTMENT' }
      },
      units: 0,
      positionGainLossPercentage: null,
      valueSecurity: null
    }
  ],
  currencypairPositionList: [
    { securitycurrency: { idSecuritycurrency: 20, fromCurrency: 'EUR', toCurrency: 'CHF', sLast: 0.9312 } }
  ]
};

describe('mobileWatchlistRows', () => {
  it('adds the holding line only to securities held with units other than zero', () => {
    const rows = mobileWatchlistRows(group);
    expect(rows.map((row) => row.name)).toEqual(['Nestlé N', 'Novartis N', 'EUR/CHF']);
    expect(rows[0].holding).toEqual({ units: 150, gainLossPercentage: 12.4, value: 12618 });
    expect(rows[1].holding).toBeNull();
  });

  it('describes a security by asset class and instrument, a currency pair by its quote currency', () => {
    const [security, , pair] = mobileWatchlistRows(group);
    expect(security.kindKeys).toEqual(['EQUITIES', 'DIRECT_INVESTMENT']);
    expect(security.currency).toBe('CHF');
    expect(pair.kindKeys).toEqual(['CURRENCY_PAIR']);
    expect(pair.currency).toBe('CHF');
    expect(pair.holding).toBeNull();
  });
});

describe('sortMobileWatchlistRows', () => {
  const rows = mobileWatchlistRows(group);

  it('orders names by the rules of the language and changes from the largest gain, missing changes last', () => {
    expect(sortMobileWatchlistRows(rows, { key: 'name', descending: false }, 'de-CH').map((r) => r.name)).toEqual([
      'EUR/CHF',
      'Nestlé N',
      'Novartis N'
    ]);
    expect(sortMobileWatchlistRows(rows, { key: 'dailyChange', descending: true }, 'de-CH').map((r) => r.name)).toEqual(
      ['Nestlé N', 'Novartis N', 'EUR/CHF']
    );
    expect(
      sortMobileWatchlistRows(rows, { key: 'dailyChange', descending: false }, 'de-CH').map((r) => r.name)
    ).toEqual(['Novartis N', 'Nestlé N', 'EUR/CHF']);
  });

  it('keeps the order of the server when no order is chosen', () => {
    expect(sortMobileWatchlistRows(rows, null, 'de-CH')).toBe(rows);
  });
});

describe('nextMobileWatchlistSort', () => {
  it('cycles through the usual direction, the reverse one and the order of the server', () => {
    const first = nextMobileWatchlistSort(null, 'dailyChange');
    expect(first).toEqual({ key: 'dailyChange', descending: true });
    const second = nextMobileWatchlistSort(first, 'dailyChange');
    expect(second).toEqual({ key: 'dailyChange', descending: false });
    expect(nextMobileWatchlistSort(second, 'dailyChange')).toBeNull();
    expect(nextMobileWatchlistSort(second, 'name')).toEqual({ key: 'name', descending: false });
  });
});
