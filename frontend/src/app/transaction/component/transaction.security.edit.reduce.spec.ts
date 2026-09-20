import { describe, expect, it } from 'vitest';
import { Cashaccount } from '../../entities/cashaccount';
import { Securityaccount } from '../../entities/securityaccount';
import { Transaction } from '../../entities/transaction';
import { TransactionCallParam } from './transaction.call.parm';
import {
  TransactionSecurityEditDividendReduce,
  TransactionSecurityEditFinanceCost
} from './transaction.security.edit.reduce';

describe('Financing-cost security account selection', () => {
  function callParam(): TransactionCallParam {
    const param = new TransactionCallParam();
    param.transaction = Object.assign(new Transaction(), { idSecurityaccount: 189, connectedIdTransaction: 1 });
    return param;
  }

  const linkedAccount = { idSecuritycashAccount: 189 } as Securityaccount;
  const otherAccount = { idSecuritycashAccount: 190 } as Securityaccount;

  it('retains the linked account after the position is fully closed', () => {
    const handler = new TransactionSecurityEditFinanceCost(callParam());
    expect(handler.acceptSecurityaccount(linkedAccount, [], false, null)).toBe(true);
  });

  it('excludes other security accounts even if they hold the same instrument', () => {
    const handler = new TransactionSecurityEditFinanceCost(callParam());
    expect(handler.acceptSecurityaccount(otherAccount, [{ idSecurityaccount: 190, units: 100 }], false, null)).toBe(
      false
    );
  });

  it('preserves cash account eligibility', () => {
    const handler = new TransactionSecurityEditFinanceCost(callParam());
    const cashaccount = { idSecuritycashAccount: 191, currency: 'CHF' } as Cashaccount;
    expect(handler.acceptSecurityaccount(cashaccount, [], false, null)).toBe(true);
  });

  it('retains the linked account when editing a saved financing transaction', () => {
    const param = callParam();
    param.transaction.idTransaction = 3;
    const handler = new TransactionSecurityEditFinanceCost(param);
    expect(handler.acceptSecurityaccount(linkedAccount, [], false, null)).toBe(true);
  });

  it('preserves holdings-based selection when there is no linked account', () => {
    const handler = new TransactionSecurityEditFinanceCost(new TransactionCallParam());
    expect(handler.acceptSecurityaccount(linkedAccount, [], false, null)).toBe(false);
    expect(handler.acceptSecurityaccount(linkedAccount, [{ idSecurityaccount: 189, units: 100 }], false, null)).toBe(
      true
    );
  });

  it('keeps ordinary sales and dividends restricted to accounts with holdings', () => {
    const handler = new TransactionSecurityEditDividendReduce(callParam());
    expect(handler.acceptSecurityaccount(linkedAccount, [], false, null)).toBe(false);
  });
});
