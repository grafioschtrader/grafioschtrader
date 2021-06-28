package grafioschtrader.task;

import java.util.List;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.types.TaskType;

public interface ITask {

  TaskType getTaskType();

  void doWork(TaskDataChange taskDataChange);

  default boolean removeAllOtherJobsOfSameTask() {
    return false;
  }

  default List<String> getAllowedEntities() {
    return null;
  }

}
