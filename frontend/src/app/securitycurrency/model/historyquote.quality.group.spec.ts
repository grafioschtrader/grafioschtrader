import { describe, expect, it } from 'vitest';
import { pickHistoryquoteQualityIds } from './historyquote.quality.group';

describe('history quote quality query projection', () => {
  it('keeps zero and null dimensions while excluding presentation data from the request', () => {
    const group = {
      idConnectorHistory: null,
      idStockexchange: 0,
      categoryType: 0,
      specialInvestmentInstrument: null,
      uniqueKey: 0,
      name: 'Displayed group',
      numberOfSecurities: 12,
      childrendHqg: []
    };
    const ids = pickHistoryquoteQualityIds(group);
    expect(ids).toEqual({
      idConnectorHistory: null,
      idStockexchange: 0,
      categoryType: 0,
      specialInvestmentInstrument: null,
      uniqueKey: 0
    });
    ids.idStockexchange = 7;
    expect(group.idStockexchange).toBe(0);
  });

  it('retains an absent client key without adding one to the serialized request', () => {
    const ids = pickHistoryquoteQualityIds({
      idConnectorHistory: 'provider',
      idStockexchange: null,
      categoryType: null,
      specialInvestmentInstrument: null
    });
    expect(ids.uniqueKey).toBeUndefined();
    expect(JSON.parse(JSON.stringify(ids))).toEqual({
      idConnectorHistory: 'provider',
      idStockexchange: null,
      categoryType: null,
      specialInvestmentInstrument: null
    });
  });
});
