import { Auditable } from './auditable';
import { BaseID } from './base.id';

/**
 * One configured anti-flooding limit. The first five properties form the limit key; idRole and idUser are mutually
 * exclusive and say who the row applies to, with both unset making it the default row of that key.
 *
 * The key is never edited field by field: the edit form posts keyId and the backend expands it into the five key
 * columns after checking it against the limit key catalogue.
 */
export class EntityLimit extends Auditable implements BaseID {
  idEntityLimit?: number;
  keyId?: string | null = null;
  limitType?: string | null = null;
  entityName?: string | null = null;
  relationEntityName?: string | null = null;
  countScope?: string | null = null;
  ownerScope?: string | null = null;
  idRole?: number | null = null;
  idUser?: number | null = null;
  limitValue?: number | null = null;
  validUntil?: Date | null = null;

  /**
   * Translation keys the backend derives for the three limit enums. They are prefixed with the enum name because
   * MAX, ALL and TENANT are already taken by unrelated texts.
   */
  limitTypeKey?: string;
  countScopeKey?: string;
  ownerScopeKey?: string;

  public override getId(): number | undefined {
    return this.idEntityLimit;
  }
}
