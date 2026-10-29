import { BaseID } from './base.id';
import { EntityLimit } from './entity.limit';
import { ProposeUserTask } from './propose.user.task';
import { Auditable } from './auditable';

export class User extends Auditable implements BaseID {
  idUser: number;
  nickname: string | null = null;
  email: string | null = null;
  password: string | null = null;
  enabled = false;
  localeStr: string | null = null;
  timezoneOffset: number;
  mostPrivilegedRole: string;
  securityBreachCount: number;
  limitRequestExceedCount: number;
  uiShowMyProperty: boolean;
  entityLimitList: EntityLimit[];
  userChangePropose: ProposeUserTask;
  userChangeLimitProposeList: ProposeUserTask[];

  public override getId(): number {
    return this.idUser;
  }
}
