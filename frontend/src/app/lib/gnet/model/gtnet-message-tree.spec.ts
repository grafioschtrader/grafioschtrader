import { describe, expect, it } from 'vitest';
import { GTNetMessage } from './gtnet.message';
import { buildGTNetMessageTree } from './gtnet-message-tree';

function message(id: number, replyTo: number | null = null): GTNetMessage {
  return Object.assign(new GTNetMessage(), { idGtNetMessage: id, replyTo });
}

describe('GTNet reply tree', () => {
  it('attaches a reply received before its parent, including a parent id of zero', () => {
    const reply = message(2, 0);
    const parent = message(0);
    const tree = buildGTNetMessageTree([reply, parent]);
    expect(tree.roots.map((node) => node.data)).toEqual([parent]);
    expect(tree.roots[0].children?.map((node) => node.data)).toEqual([reply]);
    expect(tree.roots[0].leaf).toBe(false);
  });

  it('keeps orphaned and self-referencing messages accessible and skips unsaved messages', () => {
    const orphan = message(1, 99);
    const self = message(2, 2);
    const tree = buildGTNetMessageTree([new GTNetMessage(), orphan, self]);
    expect(tree.roots.map((node) => node.data)).toEqual([orphan, self]);
    expect(tree.roots.every((node) => node.leaf)).toBe(true);
  });
});
