package grafiosch.repository;

import grafiosch.dto.TaskDataChangeFormConstraints;
import grafiosch.entities.TaskDataChange;

public interface TaskDataChangeJpaRepositoryCustom extends BaseRepositoryCustom<TaskDataChange> {

  TaskDataChangeFormConstraints getFormConstraints();

}
