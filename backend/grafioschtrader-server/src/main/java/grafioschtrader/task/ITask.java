package grafioschtrader.task;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.types.TaskType;

public interface ITask {
  
  TaskType getTaskType(); 
  
  void doWork(TaskDataChange taskDataChange);
  
  default boolean removeAllOtherJobsOfSameTask() {
    return false;
  }
  
 
}
