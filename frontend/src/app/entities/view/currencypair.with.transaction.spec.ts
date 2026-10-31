import { describe, expect, it } from 'vitest';
import { Currencypair } from '../currencypair';
import { Transaction } from '../transaction';
import { CurrencypairWithTransaction, type CurrencypairTransactionData } from './currencypair.with.transaction';

describe('currency-pair transaction chart adapter', () => {
  it('keeps report values and reverse transactions while reconstructing the instrument', () => {
    const pair = new Currencypair('EUR', 'CHF');
    pair.idSecuritycurrency = 17;
    pair.sLast = 0.94;
    pair.sTimestamp = '2026-10-30T12:00:00';
    const transaction = new Transaction();
    transaction.cashaccountAmount = -940;
    const reverse: CurrencypairTransactionData = {
      currencypair: new Currencypair('CHF', 'EUR'),
      transactionList: [],
      sumAmountFrom: -47,
      sumAmountTo: 50,
      gainTo: -2,
      gainFrom: 1,
      cwtReverse: null
    };
    const data: CurrencypairTransactionData = {
      currencypair: pair,
      transactionList: [transaction],
      sumAmountFrom: -1000,
      sumAmountTo: 940,
      gainTo: 3,
      gainFrom: -4,
      cwtReverse: reverse
    };
    const adapted = new CurrencypairWithTransaction(data);
    expect(adapted.getName()).toBe('EUR/CHF');
    expect(adapted.getSecuritycurrency()).toBeInstanceOf(Currencypair);
    expect(adapted.currencypair).not.toBe(pair);
    expect([
      adapted.currencypair.idSecuritycurrency,
      adapted.currencypair.sLast,
      adapted.currencypair.sTimestamp
    ]).toEqual([17, 0.94, '2026-10-30T12:00:00']);
    expect(adapted.transactionList[0].cashaccountAmount).toBe(-940);
    expect([adapted.sumAmountFrom, adapted.sumAmountTo, adapted.gainTo, adapted.gainFrom]).toEqual([-1000, 940, 3, -4]);
    expect(adapted.cwtReverse).toBe(reverse);
  });

  it('preserves empty transactions, zero amounts and an absent reverse pair', () => {
    const adapted = new CurrencypairWithTransaction({
      currencypair: new Currencypair('CHF', 'USD'),
      transactionList: [],
      sumAmountFrom: 0,
      sumAmountTo: 0,
      gainTo: 0,
      gainFrom: 0,
      cwtReverse: null
    });
    expect(adapted.getName()).toBe('CHF/USD');
    expect(adapted.transactionList).toEqual([]);
    expect(adapted.cwtReverse).toBeNull();
    expect([adapted.sumAmountFrom, adapted.sumAmountTo, adapted.gainTo, adapted.gainFrom]).toEqual([0, 0, 0, 0]);
  });
});
