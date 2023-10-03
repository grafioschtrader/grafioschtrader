package grafioschtrader.repository;

import grafioschtrader.dto.TaskDataChangeFormConstraints;
import grafioschtrader.dto.TaskDataChangeSecurityInfo;
import grafioschtrader.entities.TaskDataChange;

public interface TaskDataChangeJpaRepositoryCustom extends BaseRepositoryCustom<TaskDataChange> {
  
  TaskDataChangeFormConstraints getFormConstraints();
  
  TaskDataChangeSecurityInfo getAllTaskDataChangeSecurityCurrencyPairInfo();
  
 
}
