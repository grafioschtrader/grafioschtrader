import { describe, expect, it } from 'vitest';
import { Transaction } from '../../entities/transaction';
import { transactionMenuPermissions } from './transaction.menu.permissions';

const booking = (transactionType: string, simulationOpening = false, connectedIdTransaction?: number): Transaction =>
  Object.assign(new Transaction(), { idTransaction: 1, transactionType, simulationOpening, connectedIdTransaction });

describe('transaction menu permissions', () => {
  it('protects an opening deposit while allowing a standing order to be created from it', () => {
    expect(transactionMenuPermissions(booking('DEPOSIT', true))).toEqual({
      edit: false,
      delete: false,
      transfer: false,
      standingOrder: true,
      taxableInterest: false
    });
  });

  it('protects an opening dividend including its taxable flag', () => {
    expect(transactionMenuPermissions(booking('DIVIDEND', true))).toEqual({
      edit: false,
      delete: false,
      transfer: false,
      standingOrder: true,
      taxableInterest: false
    });
  });

  it.each([
    ['DEPOSIT', true, true, false],
    ['WITHDRAWAL', true, true, false],
    ['ACCUMULATE', false, true, false],
    ['REDUCE', false, true, false],
    ['DIVIDEND', false, true, true],
    ['FINANCE_COST', false, true, false],
    ['INTEREST_CASHACCOUNT', false, false, true],
    ['FEE', false, false, false]
  ])('retains the existing rules for ordinary %s bookings', (type, transfer, standingOrder, taxableInterest) => {
    expect(transactionMenuPermissions(booking(type as string))).toEqual({
      edit: true,
      delete: true,
      transfer,
      standingOrder,
      taxableInterest
    });
  });

  it('retains connected-transfer and caller-specific deletion restrictions', () => {
    expect(transactionMenuPermissions(booking('DEPOSIT', false, 2), false)).toEqual({
      edit: true,
      delete: false,
      transfer: false,
      standingOrder: true,
      taxableInterest: false
    });
  });

  it.each([null, new Transaction(), Object.assign(new Transaction(), { idTransaction: 0 })])(
    'disables actions without a persisted booking',
    (transaction) => {
      expect(Object.values(transactionMenuPermissions(transaction))).toEqual([false, false, false, false, false]);
    }
  );
});
