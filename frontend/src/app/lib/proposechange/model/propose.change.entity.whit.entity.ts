import { ProposeChangeEntity } from '../../entities/propose.change.entity';
import { ProposeUserTask } from '../../entities/propose.user.task';

export interface ProposeChangeEntityWithEntity {
  proposeChangeEntity: ProposeChangeEntity | ProposeUserTask;
  entity: any;
  proposedEntity: any;
}
