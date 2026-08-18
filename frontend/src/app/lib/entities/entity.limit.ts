import {Auditable} from './auditable';
import {BaseID} from './base.id';

/**
 * One configured anti-flooding limit. The first five properties form the limit key; idRole and idUser are mutually
 * exclusive and say who the row applies to, with both unset making it the default row of that key.
 *
 * The key is never edited field by field: the edit form posts keyId and the backend expands it into the five key
 * columns after checking it against the limit key catalogue.
 */
export class EntityLimit extends Auditable implements BaseID {
  idEntityLimit?: number;
  keyId?: string = null;
  limitType?: string = null;
  entityName?: string = null;
  relationEntityName?: string = null;
  countScope?: string = null;
  ownerScope?: string = null;
  idRole?: number = null;
  idUser?: number = null;
  limitValue?: number = null;
  validUntil?: Date = null;

  /**
   * Translation keys the backend derives for the three limit enums. They are prefixed with the enum name because
   * MAX, ALL and TENANT are already taken by unrelated texts.
   */
  limitTypeKey?: string;
  countScopeKey?: string;
  ownerScopeKey?: string;

  public override getId(): number {
    return this.idEntityLimit;
  }
}
