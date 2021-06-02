package grafioschtrader.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.types.TaskType;

public interface TaskDataChangeJpaRepositoryCustom extends BaseRepositoryCustom<TaskDataChange> {
  TaskDataChangeFormConstraints getFormConstraints();
  
  
  public static class TaskDataChangeFormConstraints {
    public Map<TaskType, List<String>> taskTypeConfig = new HashMap<>();
    public int maxUserCreateTask = 30;
    public int maxDaysInFuture = 32;
    
  }
}
