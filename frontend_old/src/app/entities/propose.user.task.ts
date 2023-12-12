import {UserTaskType} from '../shared/types/user.task.type';
import {ProposeRequest} from './propose.request';

export class ProposeUserTask extends ProposeRequest {
  idRoleTo: number;

  constructor(public userTaskType: UserTaskType) {
    super();
  }

}
