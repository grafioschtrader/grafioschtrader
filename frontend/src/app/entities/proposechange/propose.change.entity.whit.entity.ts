import {ProposeChangeEntity} from '../propose.change.entity';
import {ProposeUserTask} from '../propose.user.task';

export class ProposeChangeEntityWithEntity {
  public proposeChangeEntity: ProposeChangeEntity | ProposeUserTask;
  public entity: any;
  public proposedEntity: any;
}
