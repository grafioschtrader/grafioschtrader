package grafioschtrader.task;

import java.util.List;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.types.TaskType;

public interface ITask {

  TaskType getTaskType();

  void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException;

  default boolean removeAllOtherJobsOfSameTask() {
    return false;
  }

  default List<String> getAllowedEntities() {
    return null;
  }
  
  default boolean canBeInterrupted() {
    return false;
  }

  default long getTimeoutInSeconds() {
    return 0L;
  }
  
}
