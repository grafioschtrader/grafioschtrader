package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.types.TaskType;

public class TaskDataChangeFormConstraints {
  public Map<TaskType, List<String>> taskTypeConfig = new HashMap<>();
  public List<TaskType> canBeInterruptedList = new ArrayList<>();
  public int maxUserCreateTask = 30;
  public int maxDaysInFuture = 32;
}
