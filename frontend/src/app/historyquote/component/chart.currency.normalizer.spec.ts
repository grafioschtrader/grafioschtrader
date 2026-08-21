import {describe, expect, it} from 'vitest';
import {ChartCurrencyNormalizer, NormalizableQuoteSeries} from './chart.currency.normalizer';
import {CrossRateResponse, CurrenciesAndClosePrice} from '../../securitycurrency/service/currencypair.service';
import {HistoryquoteDateClose} from '../../entities/projection/historyquote.date.close';

/**
 * Builds a rate history for one currency pair. Only the fields the normalizer reads are populated,
 * the currency pair entity carries many more.
 */
function crossRate(fromCurrency: string, toCurrency: string, closeAndDateList: HistoryquoteDateClose[]): CurrenciesAndClosePrice {
  return {currencypair: <any>{fromCurrency, toCurrency}, closeAndDateList};
}

function response(mainCurrency: string, ...currenciesAndClosePrice: CurrenciesAndClosePrice[]): CrossRateResponse {
  return {mainCurrency, currenciesAndClosePrice};
}

function series(currencySecurity: string, historyquotes: HistoryquoteDateClose[]): NormalizableQuoteSeries {
  return {currencySecurity, historyquotes, historyquotesNorm: historyquotes};
}

const THREE_DAYS = ['2026-01-05', '2026-01-06', '2026-01-07'];

/** Three quotes of 100, 200 and 400 over three consecutive days. */
function quotes(): HistoryquoteDateClose[] {
  return [{date: THREE_DAYS[0], close: 100}, {date: THREE_DAYS[1], close: 200}, {date: THREE_DAYS[2], close: 400}];
}

/** A constant rate over the same three days, so an expected result stays easy to read. */
function rates(rate: number): HistoryquoteDateClose[] {
  return THREE_DAYS.map(date => ({date, close: rate}));
}

describe('ChartCurrencyNormalizer', () => {

  describe('no conversion required', () => {

    it('keeps the original array when no currency was requested', () => {
      const normalizer = new ChartCurrencyNormalizer();
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, '');
      expect(usdSeries.historyquotesNorm).toBe(usdSeries.historyquotes);
    });

    it('keeps the original array when the series already is in the requested currency', () => {
      const normalizer = new ChartCurrencyNormalizer();
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'USD');
      expect(usdSeries.historyquotesNorm).toBe(usdSeries.historyquotes);
    });

    it('reports a currency pair series as not needing conversion', () => {
      const normalizer = new ChartCurrencyNormalizer();
      expect(normalizer.isNormalizeNotNeeded(series(null, quotes()), 'CHF')).toBe(true);
    });

    it('skips currency pairs but converts securities in normalizeAll', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF', rates(0.8))));
      const pairSeries = series(null, quotes());
      const usdSeries = series('USD', quotes());
      normalizer.normalizeAll([pairSeries, usdSeries], 'CHF');
      expect(pairSeries.historyquotesNorm).toBe(pairSeries.historyquotes);
      expect(usdSeries.historyquotesNorm.map(h => h.close)).toEqual([80, 160, 320]);
    });
  });

  describe('requested currency is the main currency', () => {

    it('multiplies when the pair points from the security currency to the main currency', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF', rates(0.8))));
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'CHF');
      expect(usdSeries.historyquotesNorm.map(h => h.close)).toEqual([80, 160, 320]);
    });

    it('divides when the same pair is delivered in the reverse direction', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('CHF', 'USD', rates(1.25))));
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'CHF');
      expect(usdSeries.historyquotesNorm.map(h => h.close)).toEqual([80, 160, 320]);
    });
  });

  describe('requested currency differs from the main currency', () => {

    it('uses the single pair when the series is denominated in the main currency', () => {
      // Main CHF, security also CHF, user asks for EUR. The EUR/CHF pair converts on its own, no detour
      // through a second pair is needed.
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('EUR', 'CHF', rates(1.25))));
      const chfSeries = series('CHF', quotes());
      normalizer.normalize(chfSeries, 'EUR');
      expect(chfSeries.historyquotesNorm.map(h => h.close)).toEqual([80, 160, 320]);
    });

    it('converts through the main currency when it sits in the middle', () => {
      // Requested EUR, main CHF, security USD: USD -> CHF (times 0.8), then CHF -> EUR (times 1 / 1.05 by
      // way of the EUR/CHF pair being held in the other direction).
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF',
        crossRate('USD', 'CHF', rates(0.8)),
        crossRate('EUR', 'CHF', rates(1.05))));
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'EUR');
      const expected = [100, 200, 400].map(close => close * 0.8 * (1 / 1.05));
      usdSeries.historyquotesNorm.forEach((h, i) => expect(h.close).toBeCloseTo(expected[i], 10));
    });
  });

  describe('date alignment', () => {

    it('drops quote days that have no exchange rate', () => {
      const normalizer = new ChartCurrencyNormalizer();
      // The rate list is missing 2026-01-06.
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF',
        [{date: THREE_DAYS[0], close: 0.8}, {date: THREE_DAYS[2], close: 0.5}])));
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'CHF');
      expect(usdSeries.historyquotesNorm.map(h => h.date)).toEqual([THREE_DAYS[0], THREE_DAYS[2]]);
      expect(usdSeries.historyquotesNorm.map(h => h.close)).toEqual([80, 200]);
    });

    it('ignores rate days that have no quote', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF',
        [{date: '2026-01-02', close: 0.9}, ...rates(0.8)])));
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'CHF');
      expect(usdSeries.historyquotesNorm.map(h => h.close)).toEqual([80, 160, 320]);
    });

    it('leaves out quotes without a close value', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF', rates(0.8))));
      const usdSeries = series('USD',
        [{date: THREE_DAYS[0], close: 100}, {date: THREE_DAYS[1], close: null}, {date: THREE_DAYS[2], close: 400}]);
      normalizer.normalize(usdSeries, 'CHF');
      expect(usdSeries.historyquotesNorm.map(h => h.date)).toEqual([THREE_DAYS[0], THREE_DAYS[2]]);
      expect(usdSeries.historyquotesNorm.map(h => h.close)).toEqual([80, 320]);
    });

    it('does not modify the raw quotes', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF', rates(0.8))));
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'CHF');
      expect(usdSeries.historyquotes.map(h => h.close)).toEqual([100, 200, 400]);
    });
  });

  describe('cross rate bookkeeping', () => {

    it('reports no pairs before a response was taken over', () => {
      expect(new ChartCurrencyNormalizer().getExistingCurrencypairKeys()).toEqual([]);
    });

    it('encodes the held pairs for the next request and keeps the main currency', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF',
        crossRate('USD', 'CHF', rates(0.8)),
        crossRate('EUR', 'CHF', rates(1.05))));
      expect(normalizer.getMainCurrency()).toBe('CHF');
      expect(normalizer.getExistingCurrencypairKeys().sort()).toEqual(['EUR|CHF', 'USD|CHF']);
    });

    it('replaces the rate history of a pair delivered a second time', () => {
      const normalizer = new ChartCurrencyNormalizer();
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF', rates(0.8))));
      normalizer.setCrossRates(response('CHF', crossRate('USD', 'CHF', rates(0.5))));
      expect(normalizer.getExistingCurrencypairKeys()).toEqual(['USD|CHF']);
      const usdSeries = series('USD', quotes());
      normalizer.normalize(usdSeries, 'CHF');
      expect(usdSeries.historyquotesNorm.map(h => h.close)).toEqual([50, 100, 200]);
    });
  });
});
