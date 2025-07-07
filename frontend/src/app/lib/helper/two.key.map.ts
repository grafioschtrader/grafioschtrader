/**
 * Generic two-dimensional key-value map data structure.
 * Provides a nested mapping where values are accessed using two string keys,
 * creating a structure similar to map[primaryKey][secondaryKey] = value.
 *
 * This data structure is useful for organizing data that has natural two-dimensional
 * relationships, such as user preferences by category and setting name, cached data
 * by resource type and identifier, or configuration values by module and property.
 *
 * Key features:
 * - Type-safe generic implementation
 * - Automatic nested object creation
 * - Null-safe value retrieval
 * - Bulk operations for keys and values
 * - Memory-efficient property management
 *
 * @template T The type of values stored in the map
 *
 * @example
 * // Configuration management by module and property
 * const config = new TwoKeyMap<string>();
 * config.set('database', 'host', 'localhost');
 * config.set('database', 'port', '5432');
 * config.set('logging', 'level', 'info');
 * config.set('logging', 'format', 'json');
 *
 * const dbHost = config.get('database', 'host'); // 'localhost'
 * const modules = config.keys(); // ['database', 'logging']
 * const dbSettings = config.nestedKeys(); // [['host', 'port'], ['level', 'format']]
 */
export class TwoKeyMap<T> {
  /** Internal nested object structure for storing the two-dimensional mapping */
  private readonly map: object;

  constructor() {
    this.map = new Object();
  }

  /**
   * Retrieves a value using two keys with null-safe access.
   * Returns null if either the primary key doesn't exist, the nested key doesn't exist,
   * or if the value itself is null/undefined.
   *
   * @param key Primary key for the first level of mapping
   * @param nestedKey Secondary key for the second level of mapping
   * @returns The stored value of type T, or null if not found
   *
   * @example
   * const map = new TwoKeyMap<number>();
   * map.set('scores', 'math', 95);
   *
   * const mathScore = map.get('scores', 'math'); // 95
   * const missingScore = map.get('scores', 'history'); // null
   * const invalidCategory = map.get('grades', 'math'); // null
   */
  public get(key: string, nestedKey: string): T {
    return (!this.map[key] || this.map[key] && !this.map[key][nestedKey]) ? null : this.map[key][nestedKey];
  }

  /**
   * Stores a value using two keys, creating nested structure as needed.
   * Automatically creates the primary key object if it doesn't exist.
   * Uses Object.defineProperty for proper property configuration with
   * configurable and enumerable set to true.
   *
   * @param key Primary key for the first level of mapping
   * @param nestedKey Secondary key for the second level of mapping
   * @param value Value of type T to store
   */
  public set(key: string, nestedKey: string, value: T): void {
    if (!this.map[key]) {
      this.map[key] = new Object();
    }
    Object.defineProperty(this.map[key], nestedKey, {value, configurable: true, enumerable: true});
  }

  /**
   * Removes a specific value using two keys.
   * Safely handles cases where the primary key doesn't exist.
   * Does not remove the primary key object even if it becomes empty.
   *
   * @param key Primary key for the first level of mapping
   * @param nestedKey Secondary key for the second level of mapping
   */
  public remove(key, nestedKey): void {
    if (!this.map[key]) {
      return;
    }
    delete this.map[key][nestedKey];
  }

  /**
   * Returns all values from the map flattened into a single array.
   * Traverses all primary keys and extracts all nested values into one array.
   * The order of values is not guaranteed and depends on object key iteration order.
   *
   * @returns Flattened array containing all stored values of type T
   *
   * @example
   * const inventory = new TwoKeyMap<number>();
   * inventory.set('electronics', 'phones', 50);
   * inventory.set('electronics', 'laptops', 25);
   * inventory.set('clothing', 'shirts', 100);
   * inventory.set('clothing', 'pants', 75);
   *
   * const allQuantities = inventory.getValues(); // [50, 25, 100, 75] (order may vary)
   * const totalItems = allQuantities.reduce((sum, qty) => sum + qty, 0); // 250
   */
  public getValues(): T[] {
    return [].concat.apply([], Object.getOwnPropertyNames(this.map).map(key => Object.values(this.map[key])));
  }

  /**
   * Returns an array of all primary keys in the map.
   * Provides access to the first-level keys for iteration or validation purposes.
   *
   * @returns Array of all primary key strings
   *
   * @example
   * const gameStats = new TwoKeyMap<number>();
   * gameStats.set('player1', 'score', 1500);
   * gameStats.set('player1', 'lives', 3);
   * gameStats.set('player2', 'score', 1200);
   *
   * const players = gameStats.keys(); // ['player1', 'player2']
   * players.forEach(player => {
   *   console.log(`Player: ${player}`);
   * });
   */
  public keys(): string[] {
    return Object.getOwnPropertyNames(this.map);
  }

  /**
   * Returns arrays of nested keys for each primary key.
   * Provides a way to inspect the structure and see what nested keys exist
   * under each primary key. The returned array corresponds to the order of keys().
   *
   * @returns Array of string arrays, where each inner array contains the nested keys for a primary key
   */
  public nestedKeys(): Array<string[]> {
    return Object.getOwnPropertyNames(this.map).map(key => Object.keys(this.map[key]));
  }

  /**
   * Removes all data from the map, resetting it to empty state.
   * Deletes all primary key objects and their nested properties.
   * After calling clear(), the map will be equivalent to a newly constructed instance.
   */
  public clear(): void {
    Object.getOwnPropertyNames(this.map).forEach(property => {
      delete this.map[property];
    });
  }
}
