import { describe, it, expect } from 'vitest';
import { Helper } from './helper';
import { BaseSettings } from '../base.settings';
import { FieldConfig } from '../dynamic-form/models/field.config';

describe('Helper.parseDateOnlyAsLocal', () => {
  it.each(['2026-03-01', '2026-09-15'])('loads %s at local midnight and preserves it across save cycles', (value) => {
    let stored = value;
    for (let cycle = 0; cycle < 2; cycle++) {
      const date = Helper.parseDateOnlyAsLocal(stored);
      const [year, month, day] = value.split('-').map(Number);
      expect([date.getFullYear(), date.getMonth() + 1, date.getDate()]).toEqual([year, month, day]);
      expect([date.getHours(), date.getMinutes(), date.getSeconds(), date.getMilliseconds()]).toEqual([0, 0, 0, 0]);
      stored = Helper.formatDateStringAsString(
        { formControl: { value: date } } as FieldConfig,
        BaseSettings.FORMAT_DATE_SHORT_NATIVE
      );
      expect(stored).toBe(value);
    }
  });

  it.each(['2026-09-15T20:00:00Z', '2026-09-15T10:30:00', 1789502400000, new Date(1789502400000)])(
    'preserves native Date parsing for %s',
    (value) => {
      expect(Helper.parseDateOnlyAsLocal(value).getTime()).toBe(new Date(value).getTime());
    }
  );
});

describe('Helper.getValueByPath', () => {
  it('returns value for simple path', () => {
    expect(Helper.getValueByPath({ name: 'test' }, 'name')).toBe('test');
  });

  it('returns value for nested path', () => {
    expect(Helper.getValueByPath({ a: { b: { c: 42 } } }, 'a.b.c')).toBe(42);
  });

  it('returns null for missing intermediate', () => {
    expect(Helper.getValueByPath({ a: {} }, 'a.b.c')).toBeNull();
  });

  it('returns null/undefined for null dataObject', () => {
    expect(Helper.getValueByPath(null, 'a')).toBeNull();
  });

  it('returns undefined for undefined dataObject', () => {
    expect(Helper.getValueByPath(undefined, 'a')).toBeUndefined();
  });
});

describe('Helper.setValueByPath', () => {
  it('sets value for simple path', () => {
    const obj: any = {};
    Helper.setValueByPath(obj, 'name', 'hello');
    expect(obj.name).toBe('hello');
  });

  it('sets value for nested path', () => {
    const obj: any = {};
    Helper.setValueByPath(obj, 'a.b.c', 99);
    expect(obj.a.b.c).toBe(99);
  });

  it('creates intermediate objects', () => {
    const obj: any = {};
    Helper.setValueByPath(obj, 'x.y', 'val');
    expect(obj.x).toBeDefined();
    expect(obj.x.y).toBe('val');
  });
});

describe('Helper.flattenObject', () => {
  it('flattens nested object', () => {
    const result = Helper.flattenObject({ a: 1, b: { c: 2 } });
    expect(result['a']).toBe(1);
    expect(result['c']).toBe(2);
  });

  it('returns empty object for empty input', () => {
    expect(Helper.flattenObject({})).toEqual({});
  });
});

describe('Helper.findPropertyNamesInObjectTree', () => {
  it('finds property at root level', () => {
    const result = Helper.findPropertyNamesInObjectTree({ name: 'val' }, 'name');
    expect(result).toEqual(['name']);
  });

  it('finds property at nested level', () => {
    const result = Helper.findPropertyNamesInObjectTree({ a: { target: 'found' } }, 'target');
    expect(result).toEqual(['a.target']);
  });

  it('finds property at multiple levels', () => {
    const result = Helper.findPropertyNamesInObjectTree({ id: 1, child: { id: 2 } }, 'id');
    expect(result).toContain('id');
    expect(result).toContain('child.id');
  });

  it('returns empty array when not found', () => {
    expect(Helper.findPropertyNamesInObjectTree({ a: 1 }, 'missing')).toEqual([]);
  });
});
