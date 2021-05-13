import {BaseID} from './base.id';
import {UserEntityChangeLimit} from './user.entity.change.limit';
import {ProposeUserTask} from './propose.user.task';
import {Auditable} from './auditable';

export class User extends Auditable implements BaseID {
  idUser: number;
  nickname: string = null;
  email: string = null;
  password: string = null;
  enabled = false;
  localeStr: string = null;
  timezoneOffset: number;
  mostPrivilegedRole: string;
  securityBreachCount: number;
  limitRequestExceedCount: number;
  uiShowMyProperty: boolean;
  userEntityChangeLimitList: UserEntityChangeLimit[];
  userChangePropose: ProposeUserTask;
  userChangeLimitProposeList: ProposeUserTask[];

  public getId(): number {
    return this.idUser;
  }
}
