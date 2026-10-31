import { describe, expect, it } from 'vitest';
import { MobileWatchlistGroup } from '../watchlist/mobile.watchlist.rows';
import { findMobileWatchlistInstrument, mobileInstrumentSections } from './mobile.instrument.details';
import { mobileTransactionRows, mobileTransactionSummarySection } from './mobile.instrument.transactions';

const group: MobileWatchlistGroup = {
  securityPositionList: [
    {
      securitycurrency: {
        idSecuritycurrency: 10,
        name: 'Nestlé N',
        currency: 'CHF',
        isin: 'CH0038863350',
        tickerSymbol: 'NESN',
        sLast: 84.12,
        sChangePercentage: 1.24,
        leverageFactor: 1,
        stockexchange: { name: 'SIX Swiss Exchange' },
        assetClass: {
          categoryType: 'EQUITIES',
          specialInvestmentInstrument: 'DIRECT_INVESTMENT',
          subCategoryNLS: { map: { de: 'Basiskonsumgüter', en: 'Consumer staples' } }
        }
      },
      units: 150,
      valueSecurity: 12618,
      positionGainLoss: 1392,
      positionGainLossPercentage: 12.4,
      ytdChangePercentage: null,
      watchlistSecurityHasEver: true
    },
    {
      securitycurrency: {
        idSecuritycurrency: 11,
        name: 'Long Mini Future',
        currency: 'CHF',
        isin: 'CH1111111111',
        idLinkSecuritycurrency: 10,
        leverageFactor: 3
      },
      units: 0
    }
  ],
  currencypairPositionList: [
    { securitycurrency: { idSecuritycurrency: 20, fromCurrency: 'EUR', toCurrency: 'CHF', sLast: 0.9312 } }
  ]
};

function labels(idSecuritycurrency: number): Record<string, string[]> {
  const instrument = findMobileWatchlistInstrument(group, idSecuritycurrency);
  if (!instrument) {
    throw new Error(`instrument ${idSecuritycurrency} missing`);
  }
  return Object.fromEntries(
    mobileInstrumentSections(instrument, 'de').map((s) => [s.titleKey, s.entries.map((e) => e.labelKey)])
  );
}

describe('mobileInstrumentSections', () => {
  it('shows the position of a held security and omits the lines without a value', () => {
    const sections = labels(10);
    expect(sections['HOLDINGS']).toEqual(['QUANTITY', 'VALUE_SECURITY', 'GAIN', 'GAIN_PERCENTAGE']);
    expect(sections['QUOTATION_DATA']).toEqual(['LAST', 'DAILY_CHANGE']);
    expect(sections['BASE_DATA']).toEqual([
      'ASSETCLASS',
      'FINANCIAL_INSTRUMENT',
      'SUB_ASSETCLASS',
      'STOCKEXCHANGE',
      'CURRENCY',
      'ISIN',
      'TICKER_SYMBOL'
    ]);
  });

  it('gives a derived instrument neither position nor ISIN, but its leverage factor', () => {
    const sections = labels(11);
    expect(sections['HOLDINGS']).toBeUndefined();
    expect(sections['BASE_DATA']).toEqual(['CURRENCY', 'LEVERAGE_FACTOR']);
  });

  it('describes a currency pair by its two currencies', () => {
    expect(labels(20)['BASE_DATA']).toEqual(['CURRENCY_FROM', 'CURRENCY_TO']);
  });

  it('finds no instrument the watchlist no longer contains', () => {
    expect(findMobileWatchlistInstrument(group, 99)).toBeNull();
  });
});

describe('mobileTransactionRows', () => {
  const summary = {
    transactionPositionList: [
      { transaction: { idTransaction: 1, transactionTime: '2024-03-01T10:00:00', transactionType: 'ACCUMULATE' } },
      {
        transaction: { idTransaction: 2, transactionTime: '2025-06-02T00:00:00', transactionType: 'DIVIDEND' },
        transactionGainLossPercentage: null
      },
      {
        transaction: {
          idTransaction: 3,
          transactionTime: '2024-03-01T10:00:00',
          transactionType: 'REDUCE',
          transactionCost: 0,
          taxCost: 1.5
        },
        transactionGainLossPercentage: 4.2
      }
    ],
    securityPositionSummary: { units: 150, valueSecurity: 12618, gainLossSecurity: null, transactionCost: 25 }
  };

  it('lists the newest transaction first and keeps the later booking on top at the same time', () => {
    expect(mobileTransactionRows(summary).map((row) => row.idTransaction)).toEqual([2, 3, 1]);
  });

  it('leaves a cost of zero out of the line', () => {
    const sale = mobileTransactionRows(summary).find((row) => row.idTransaction === 3);
    expect(sale?.transactionCost).toBeNull();
    expect(sale?.taxCost).toBe(1.5);
  });

  it('summarizes the position in the currency of the security', () => {
    const section = mobileTransactionSummarySection(summary, 'CHF');
    expect(section.entries.map((e) => e.labelKey)).toEqual(['QUANTITY', 'VALUE_SECURITY', 'TRANSACTION_COST']);
    expect(section.entries.every((e) => e.format !== 'amount' || e.currency === 'CHF')).toBe(true);
  });
});
