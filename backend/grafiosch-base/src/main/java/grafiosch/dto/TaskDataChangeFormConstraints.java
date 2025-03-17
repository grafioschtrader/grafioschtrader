package grafiosch.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafiosch.types.ITaskType;

public class TaskDataChangeFormConstraints {
  public Map<ITaskType, List<String>> taskTypeConfig = new HashMap<>();
  public List<ITaskType> canBeInterruptedList = new ArrayList<>();
  public int maxUserCreateTask = 30;
  public int maxDaysInFuture = 32;
}
