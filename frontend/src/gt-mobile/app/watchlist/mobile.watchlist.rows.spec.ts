import { describe, expect, it } from 'vitest';
import { MobileWatchlistGroup, mobileWatchlistRows } from './mobile.watchlist.rows';

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
