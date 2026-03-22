import { describe, it, expect } from 'vitest';
import { BusinessHelper } from './business.helper';
import { Currencypair } from '../../entities/currencypair';
import { TransactionType } from '../types/transaction.type';

describe('BusinessHelper.roundNumber', () => {
  it('rounds to 2 decimal places', () => {
    expect(BusinessHelper.roundNumber(1.005, 2)).toBe(1);
    expect(BusinessHelper.roundNumber(1.235, 2)).toBe(1.24);
    expect(BusinessHelper.roundNumber(1.2, 2)).toBe(1.2);
  });

  it('rounds to 0 decimal places', () => {
    expect(BusinessHelper.roundNumber(1.5, 0)).toBe(2);
    expect(BusinessHelper.roundNumber(1.4, 0)).toBe(1);
  });

  it('rounds to 8 decimal places', () => {
    expect(BusinessHelper.roundNumber(1.123456789, 8)).toBe(1.12345679);
  });
});

describe('BusinessHelper.divideMultiplyExchangeRate', () => {
  it('divides when sourceCurrency equals fromCurrency', () => {
    const cp = new Currencypair('USD', 'EUR');
    const result = BusinessHelper.divideMultiplyExchangeRate(100, 1.25, 'USD', cp);
    expect(result).toBe(80);
  });

  it('multiplies when sourceCurrency does not equal fromCurrency', () => {
    const cp = new Currencypair('USD', 'EUR');
    const result = BusinessHelper.divideMultiplyExchangeRate(100, 1.25, 'EUR', cp);
    expect(result).toBe(125);
  });

  it('returns rounded value when no currencypair', () => {
    const result = BusinessHelper.divideMultiplyExchangeRate(1.123456789, 1.0, 'USD', null);
    expect(result).toBe(1.12345679);
  });

  it('returns rounded value when no sourceCurrency', () => {
    const cp = new Currencypair('USD', 'EUR');
    const result = BusinessHelper.divideMultiplyExchangeRate(1.123456789, 1.0, null, cp);
    expect(result).toBe(1.12345679);
  });
});

describe('BusinessHelper.getCurrencypairWithSetOfFromAndTo', () => {
  it('creates pair for different currencies', () => {
    const result = BusinessHelper.getCurrencypairWithSetOfFromAndTo('USD', 'EUR');
    expect(result).not.toBeNull();
    expect(result.fromCurrency).toBe('EUR');
    expect(result.toCurrency).toBe('USD');
  });

  it('returns null for same currencies', () => {
    expect(BusinessHelper.getCurrencypairWithSetOfFromAndTo('EUR', 'EUR')).toBeNull();
  });

  it('returns null when source is null', () => {
    expect(BusinessHelper.getCurrencypairWithSetOfFromAndTo(null, 'EUR')).toBeNull();
  });
});

describe('BusinessHelper.getTotalAmountFromTransaction', () => {
  it('negates FEE transaction', () => {
    const tx = { cashaccountAmount: 50, transactionType: TransactionType[TransactionType.FEE] } as any;
    expect(BusinessHelper.getTotalAmountFromTransaction(tx)).toBe(-50);
  });

  it('negates WITHDRAWAL transaction', () => {
    const tx = { cashaccountAmount: 100, transactionType: TransactionType[TransactionType.WITHDRAWAL] } as any;
    expect(BusinessHelper.getTotalAmountFromTransaction(tx)).toBe(-100);
  });

  it('preserves DEPOSIT amount', () => {
    const tx = { cashaccountAmount: 200, transactionType: TransactionType[TransactionType.DEPOSIT] } as any;
    expect(BusinessHelper.getTotalAmountFromTransaction(tx)).toBe(200);
  });

  it('preserves ACCUMULATE amount', () => {
    const tx = { cashaccountAmount: 300, transactionType: TransactionType[TransactionType.ACCUMULATE] } as any;
    expect(BusinessHelper.getTotalAmountFromTransaction(tx)).toBe(300);
  });

  it('preserves DIVIDEND amount', () => {
    const tx = { cashaccountAmount: 75, transactionType: TransactionType[TransactionType.DIVIDEND] } as any;
    expect(BusinessHelper.getTotalAmountFromTransaction(tx)).toBe(75);
  });
});
