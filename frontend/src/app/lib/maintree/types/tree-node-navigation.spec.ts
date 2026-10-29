import type { TreeNode } from '@openng/optimus-ui/api';
import { describe, expect, it } from 'vitest';
import { previousTreeNode } from './tree-node-navigation';

describe('navigation after deleting a tree node', () => {
  it('selects the preceding sibling, or the parent for the first child', () => {
    const parent: TreeNode = { key: 'portfolio', children: [] };
    const first: TreeNode = { key: 'first-account', parent };
    const second: TreeNode = { key: 'second-account', parent };
    const third: TreeNode = { key: 'third-account', parent };
    parent.children = [first, second, third];
    expect(previousTreeNode(first)).toBe(parent);
    expect(previousTreeNode(second)).toBe(first);
    expect(previousTreeNode(third)).toBe(second);
  });

  it('does not choose an unrelated sibling for a stale node or navigate from a detached root', () => {
    const parent: TreeNode = { key: 'watchlists' };
    const stale: TreeNode = { key: 'removed', parent };
    expect(previousTreeNode(stale)).toBe(parent);
    parent.children = [{ key: 'unrelated', parent }];
    expect(previousTreeNode(stale)).toBe(parent);
    expect(previousTreeNode(parent)).toBeNull();
  });
});
