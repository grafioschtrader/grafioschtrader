package grafiosch.task.exec;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.UserJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskTypeBase;

/**
 * Moves shared data from one user to another. It is assumed that shared data
 * has a field 'created_by', whereby tables beginning with "user" are excluded.
 */
@Component
public class MoveCreatedByUserToOtherUserTask implements ITask {

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.MOVE_CREATED_BY_USER_TO_OTHER_USER;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    try {
      userJpaRepository.moveCreatedByUserToOtherUser(taskDataChange.getOldValueNumber().intValue(),
          taskDataChange.getIdEntity());
    } catch (SQLException sqle) {
      throw new TaskBackgroundException("gt.move.createdby.procedure.failure", true);
    }
  }

}
