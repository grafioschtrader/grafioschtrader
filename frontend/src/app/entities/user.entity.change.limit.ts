import {Auditable} from './auditable';
import {BaseID} from './base.id';

export class UserEntityChangeLimit extends Auditable implements BaseID {
  idUserEntityChangeLimit?: number;
  idUser?: number;
  entityName?: string = null;
  dayLimit?: string = null;
  untilDate?: Date = null;

  public getId(): number {
    return this.idUserEntityChangeLimit;
  }
}
