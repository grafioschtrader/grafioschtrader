package grafioschtrader.repository;

import grafioschtrader.dto.TaskDataChangeFormConstraints;
import grafioschtrader.entities.TaskDataChange;

public interface TaskDataChangeJpaRepositoryCustom extends BaseRepositoryCustom<TaskDataChange> {
  
  TaskDataChangeFormConstraints getFormConstraints();
  
}
