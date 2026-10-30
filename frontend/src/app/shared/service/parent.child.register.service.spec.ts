import { describe, expect, it } from 'vitest';
import { PageFirstRowSelectedRow, ParentChildRegisterService } from './parent.child.register.service';

describe('saved child table positions', () => {
  it('keeps a zero parent id separate from a missing parent and clears positions on registry reset', () => {
    const registry = new ParentChildRegisterService();
    registry.saveRowPosition(0, new PageFirstRowSelectedRow(20, 42));
    registry.saveRowPosition(undefined, new PageFirstRowSelectedRow(40, 99));
    expect(registry.getRowPosition(null)).toEqual(new PageFirstRowSelectedRow(0, null));
    expect(registry.getRowPosition(0)).toEqual(new PageFirstRowSelectedRow(20, 42));
    registry.initRegistry();
    expect(registry.getRowPosition(0)).toEqual(new PageFirstRowSelectedRow(0, null));
  });
});
