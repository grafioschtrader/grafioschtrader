import {ProposeChangeEntity} from '../../entities/propose.change.entity';
import {ProposeUserTask} from '../../entities/propose.user.task';

export class ProposeChangeEntityWithEntity {
  public proposeChangeEntity: ProposeChangeEntity | ProposeUserTask;
  public entity: any;
  public proposedEntity: any;
}
