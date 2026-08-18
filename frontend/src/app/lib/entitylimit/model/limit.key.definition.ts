/**
 * Everything the edit form needs to offer one limit key and validate a value entered for it, as returned by
 * GET /entitylimit/keys.
 *
 * A plain key/value option pair cannot carry this: picking a key fixes four of the five key columns as read-only
 * fields of the form, and the value the administrator types has to be validated against the rule that belongs to the
 * key rather than to the row.
 */
export interface LimitKeyDefinition {
  /**
   * Stable wire id of the key: the five key parts joined with '|', empty segment for an unset part, for example
   * 'MAX|Watchlist|Securitycurrency|SINGLE|TENANT' or 'DAY_CUD|Assetclass|||'. This is what the form posts back.
   */
  keyId: string;
  limitType: string;
  entityName: string;
  relationEntityName: string;
  countScope: string;
  ownerScope: string;
  /** UPPER_SNAKE_CASE translation key under which the key is displayed. */
  labelKey: string;
  /** Validation rule for the limit value, in the globalparameters input_rule DSL. Null when the key has none. */
  inputRule: string;
  /** Value a fresh installation seeds for this key. Null for the derived daily keys. */
  defaultValue: number;
  msgKey: string;
  /** True for a registered MAX key, whose default row may be changed but never deleted. */
  mandatoryAllRow: boolean;
  /**
   * True when the guarded entity holds data shared between all tenants, false when it belongs to one client or one
   * user, null when the backend cannot resolve the entity behind the name. It is delivered here rather than derived
   * from ownerScope, because the daily keys have no scope.
   */
  sharedData: boolean;
}
