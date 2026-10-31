import { TreeNode } from '@openng/optimus-ui/api';
import { GTNetMessage } from './gtnet.message';

/** Builds reply branches independently of message order; orphaned replies remain root messages. */
export function buildGTNetMessageTree(messages: readonly GTNetMessage[]): {
  roots: TreeNode<GTNetMessage>[];
  nodeMap: Map<number, TreeNode<GTNetMessage>>;
} {
  const nodeMap = new Map<number, TreeNode<GTNetMessage>>();
  for (const message of messages) {
    if (message.idGtNetMessage != null) nodeMap.set(message.idGtNetMessage, { data: message, leaf: true });
  }
  const roots: TreeNode<GTNetMessage>[] = [];
  for (const message of messages) {
    if (message.idGtNetMessage == null) continue;
    const node = nodeMap.get(message.idGtNetMessage);
    if (!node) continue;
    const parent = message.replyTo == null ? undefined : nodeMap.get(message.replyTo);
    if (parent && parent !== node) {
      parent.leaf = false;
      (parent.children ??= []).push(node);
    } else {
      roots.push(node);
    }
  }
  return { roots, nodeMap };
}
