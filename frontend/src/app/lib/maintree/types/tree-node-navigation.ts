import type { TreeNode } from '@openng/optimus-ui/api';

/** Destination after deletion: the preceding sibling, otherwise the parent; detached roots have none. */
export function previousTreeNode<T>(node: TreeNode<T>): TreeNode<T> | null {
  const parent = node.parent;
  if (!parent) return null;
  const siblings = parent.children;
  const index = siblings?.indexOf(node) ?? -1;
  return index > 0 && siblings ? siblings[index - 1] : parent;
}
