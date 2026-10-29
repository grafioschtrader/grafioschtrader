import { ErrorOptions } from './ngxerrors';

export const toArray = (value: ErrorOptions | null | undefined): string[] =>
  value == null ? [] : Array.isArray(value) ? value : [value];
