import {BaseID} from './base.id';
import {Auditable} from './auditable';
import {ProposeChangeField} from './propose.change.field';
import {ProposeRequest} from './propose.request';

export class ProposeChangeEntity extends ProposeRequest implements BaseID {
  idEntity: number;
  idOwnerEntity: number;

}
