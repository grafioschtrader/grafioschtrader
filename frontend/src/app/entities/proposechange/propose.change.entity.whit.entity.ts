import {ProposeChangeEntity} from '../../lib/entities/propose.change.entity';
import {ProposeUserTask} from '../../lib/entities/propose.user.task';

export class ProposeChangeEntityWithEntity {
  public proposeChangeEntity: ProposeChangeEntity | ProposeUserTask;
  public entity: any;
  public proposedEntity: any;
}
