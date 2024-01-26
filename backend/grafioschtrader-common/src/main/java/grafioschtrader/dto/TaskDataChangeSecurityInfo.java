package grafioschtrader.dto;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.TaskDataChange;

public class TaskDataChangeSecurityInfo {
  public List<TaskDataChange> taskDataChangeList;
  public Map<Integer, String> securityInfo;

  public TaskDataChangeSecurityInfo(List<TaskDataChange> taskDataChangeList, Map<Integer, String> securityInfo) {
    this.taskDataChangeList = taskDataChangeList;
    this.securityInfo = securityInfo;
  }
}