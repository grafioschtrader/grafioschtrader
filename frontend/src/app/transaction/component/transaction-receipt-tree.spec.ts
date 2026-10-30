import { describe, expect, it } from 'vitest';
import { TreeNode } from '@openng/optimus-ui/api';

import { Transaction } from '../../entities/transaction';
import { buildTransactionReceiptTree, getSelectedTransactions } from './transaction-receipt-tree';

function transaction(idTransaction: number, connectedIdTransaction?: number): Transaction {
  return Object.assign(new Transaction(), { idTransaction, connectedIdTransaction });
}

describe('transaction receipt tree', () => {
  it('excludes unsaved transactions while retaining a persisted transaction whose parent is missing', () => {
    const unsaved = new Transaction();
    const persisted = transaction(2, 99);
    const roots = buildTransactionReceiptTree([unsaved, persisted]);
    expect(roots.map((node) => node.data)).toEqual([persisted]);
    expect(getSelectedTransactions([unsaved, persisted], [{ data: unsaved }, { data: persisted }])).toEqual([
      persisted
    ]);
  });
  it('groups connected transactions below their opening transaction', () => {
    const transactions = [transaction(1), transaction(2, 1), transaction(3, 1), transaction(4)];

    const roots = buildTransactionReceiptTree(transactions);

    expect(roots.map((node) => node.data.idTransaction)).toEqual([1, 4]);
    expect(roots[0].children?.map((node) => node.data.idTransaction)).toEqual([2, 3]);
    expect(roots[0].expanded).toBe(true);
    expect(roots[1].leaf).toBe(true);
  });

  it('groups a child even when it occurs before its parent', () => {
    const roots = buildTransactionReceiptTree([transaction(2, 1), transaction(1)]);

    expect(roots).toHaveLength(1);
    expect(roots[0].data.idTransaction).toBe(1);
    expect(roots[0].children?.[0].data.idTransaction).toBe(2);
  });

  it('keeps a transaction with a missing parent selectable at root level', () => {
    const roots = buildTransactionReceiptTree([transaction(2, 99)]);

    expect(roots).toHaveLength(1);
    expect(roots[0].data.idTransaction).toBe(2);
  });

  it('returns unique persisted selections in transaction order', () => {
    const transactions = [transaction(1), transaction(2, 1), transaction(3, 1)];
    const selectedNodes = [
      { data: transaction(3) } as TreeNode,
      { data: transaction(-1) } as TreeNode,
      { data: transaction(1) } as TreeNode,
      { data: transaction(1) } as TreeNode
    ];

    expect(getSelectedTransactions(transactions, selectedNodes).map((t) => t.idTransaction)).toEqual([1, 3]);
  });
});
