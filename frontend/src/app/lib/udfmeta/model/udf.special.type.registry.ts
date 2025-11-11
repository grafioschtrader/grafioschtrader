import {IUDFSpecialType} from './udf.special.type.interface';

/**
 * Registry for managing UDF special types in the application.
 * This registry allows application-specific special types to be registered at startup
 * without creating coupling between library code and application-specific implementations.
 *
 * The registry mirrors the backend's EnumRegistry pattern for UDFSpecialGTType,
 * providing a consistent architectural approach across frontend and backend.
 *
 * Usage:
 * 1. Define special types as const objects implementing IUDFSpecialType
 * 2. Register them at application startup: UDFSpecialTypeRegistry.register(types)
 * 3. Access types by value: UDFSpecialTypeRegistry.getByValue(1)
 */
export class UDFSpecialTypeRegistry {
  private static types: Map<number, IUDFSpecialType> = new Map();

  /**
   * Registers an array of UDF special types into the registry.
   * Should be called during application initialization to populate available special types.
   *
   * @param types - Array of special type objects to register
   */
  static register(types: IUDFSpecialType[]): void {
    types.forEach(t => this.types.set(t.value, t));
  }

  /**
   * Retrieves a UDF special type by its numeric value.
   *
   * @param value - The numeric identifier of the special type
   * @returns The matching special type object, or undefined if not found
   */
  static getByValue(value: number): IUDFSpecialType | undefined {
    return this.types.get(value);
  }

  /**
   * Retrieves a UDF special type by its name identifier.
   *
   * @param name - The name identifier of the special type (case-sensitive)
   * @returns The matching special type object, or undefined if not found
   */
  static getByName(name: string): IUDFSpecialType | undefined {
    return Array.from(this.types.values()).find(t => t.name === name);
  }

  /**
   * Retrieves all registered UDF special types.
   *
   * @returns Array of all registered special type objects
   */
  static getAll(): IUDFSpecialType[] {
    return Array.from(this.types.values());
  }

  /**
   * Checks if a special type value is registered.
   *
   * @param value - The numeric identifier to check
   * @returns True if the value is registered, false otherwise
   */
  static has(value: number): boolean {
    return this.types.has(value);
  }

  /**
   * Clears all registered types from the registry.
   * Primarily useful for testing purposes.
   */
  static clear(): void {
    this.types.clear();
  }
}
