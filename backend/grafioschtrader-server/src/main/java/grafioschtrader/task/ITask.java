package grafioschtrader.task;

import grafioschtrader.types.TaskType;

public interface ITask {
  
  TaskType getTaskType(); 
  
  void doWork(Integer idEntity, String entity);
  
  default boolean removeAllOtherJobsOfSameTask() {
    return false;
  }
  
 
}
