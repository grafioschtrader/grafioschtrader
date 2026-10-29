export interface BaseID {
  /** New entities have no persisted identifier yet. */
  getId(): number | string | null | undefined;
}
