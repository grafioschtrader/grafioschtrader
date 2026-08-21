import {TreeNode} from '@openng/optimus-ui/api';

import {Transaction} from '../../entities/transaction';

/**
 * Builds receipt-selection branches from persisted margin transactions. Opening transactions become roots and
 * transactions referring to them through connectedIdTransaction become children. A missing parent leaves the
 * connected transaction at root level so it remains selectable.
 *
 * @param transactions transactions offered for receipt creation
 * @returns expanded transaction tree in the same order as the input
 */
export function buildTransactionReceiptTree(transactions: Transaction[]): TreeNode[] {
  const nodeById = new Map<number, TreeNode>();
  transactions.forEach(transaction => {
    if (transaction.idTransaction != null) {
      nodeById.set(transaction.idTransaction, {data: transaction, children: []});
    }
  });

  const roots: TreeNode[] = [];
  transactions.forEach(transaction => {
    const node = nodeById.get(transaction.idTransaction);
    if (!node) {
      return;
    }
    const parent = transaction.connectedIdTransaction == null
      ? null : nodeById.get(transaction.connectedIdTransaction);
    if (parent && parent !== node) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  });

  nodeById.forEach(node => {
    node.leaf = node.children.length === 0;
    node.expanded = !node.leaf;
    if (node.leaf) {
      node.children = null;
    }
  });
  return roots;
}

/**
 * Converts Optimus tree selection into the original transaction order while excluding duplicate or synthetic nodes.
 *
 * @param transactions persisted transactions offered for selection
 * @param selectedNodes nodes selected by Optimus
 * @returns selected persisted transactions in their original order
 */
export function getSelectedTransactions(transactions: Transaction[], selectedNodes: TreeNode[]): Transaction[] {
  const selectedIds = new Set(selectedNodes
    .map(node => node.data?.idTransaction)
    .filter(idTransaction => idTransaction != null && idTransaction > 0));
  return transactions.filter(transaction => transaction.idTransaction != null
    && selectedIds.has(transaction.idTransaction));
}
