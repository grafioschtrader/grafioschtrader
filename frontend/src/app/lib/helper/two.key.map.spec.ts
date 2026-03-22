import { describe, it, expect } from 'vitest';
import { TwoKeyMap } from './two.key.map';

describe('TwoKeyMap', () => {
  it('set/get stores and retrieves values', () => {
    const map = new TwoKeyMap<number>();
    map.set('a', 'x', 10);
    expect(map.get('a', 'x')).toBe(10);
  });

  it('get returns null for missing primary key', () => {
    const map = new TwoKeyMap<string>();
    expect(map.get('missing', 'key')).toBeNull();
  });

  it('get returns null for missing nested key', () => {
    const map = new TwoKeyMap<string>();
    map.set('a', 'x', 'val');
    expect(map.get('a', 'missing')).toBeNull();
  });

  it('set overwrites existing value', () => {
    const map = new TwoKeyMap<number>();
    map.set('a', 'x', 1);
    map.set('a', 'x', 2);
    expect(map.get('a', 'x')).toBe(2);
  });

  it('remove deletes nested key', () => {
    const map = new TwoKeyMap<number>();
    map.set('a', 'x', 1);
    map.remove('a', 'x');
    expect(map.get('a', 'x')).toBeNull();
  });

  it('remove is no-op for missing primary key', () => {
    const map = new TwoKeyMap<number>();
    expect(() => map.remove('missing', 'x')).not.toThrow();
  });

  it('getValues returns flattened array', () => {
    const map = new TwoKeyMap<number>();
    map.set('a', 'x', 1);
    map.set('a', 'y', 2);
    map.set('b', 'z', 3);
    const values = map.getValues();
    expect(values).toHaveLength(3);
    expect(values).toContain(1);
    expect(values).toContain(2);
    expect(values).toContain(3);
  });

  it('keys returns primary keys', () => {
    const map = new TwoKeyMap<string>();
    map.set('alpha', 'x', 'v1');
    map.set('beta', 'y', 'v2');
    expect(map.keys()).toEqual(['alpha', 'beta']);
  });

  it('nestedKeys returns nested key arrays', () => {
    const map = new TwoKeyMap<number>();
    map.set('a', 'x', 1);
    map.set('a', 'y', 2);
    map.set('b', 'z', 3);
    const nested = map.nestedKeys();
    expect(nested).toEqual([['x', 'y'], ['z']]);
  });

  it('clear empties the map', () => {
    const map = new TwoKeyMap<number>();
    map.set('a', 'x', 1);
    map.set('b', 'y', 2);
    map.clear();
    expect(map.keys()).toEqual([]);
    expect(map.getValues()).toEqual([]);
  });
});
