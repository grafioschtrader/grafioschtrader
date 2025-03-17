package grafiosch.task;

import java.util.List;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.types.ITaskType;

public interface ITask {

  ITaskType getTaskType();

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
