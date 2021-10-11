package grafioschtrader.task.exec;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.UserJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class MoveCreatedByUserToOtherUser implements ITask {

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.MOVE_CREATED_BY_USER_TO_OTHER_USER;
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
