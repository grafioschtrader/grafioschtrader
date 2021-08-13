import {BaseID} from './base.id';
import {ProposeRequest} from './propose.request';

export class ProposeChangeEntity extends ProposeRequest implements BaseID {
  idEntity: number;
  idOwnerEntity: number;

}
