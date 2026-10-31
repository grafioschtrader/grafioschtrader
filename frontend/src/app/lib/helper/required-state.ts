/** A required lifecycle value must be established by its owner before a consumer can read it. */
export function requireInitialized<T>(value: T | null | undefined, label: string): NonNullable<T> {
  if (value == null) throw new Error(`${label} has not been initialized`);
  return value;
}
