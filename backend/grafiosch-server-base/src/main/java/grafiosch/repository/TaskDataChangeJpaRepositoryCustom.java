package grafiosch.repository;

import grafiosch.dto.TaskDataChangeFormConstraints;
import grafiosch.entities.TaskDataChange;

/**
 * Custom repository interface for TaskDataChange entities. Provides additional repository methods beyond standard JPA
 * operations.
 */
public interface TaskDataChangeJpaRepositoryCustom extends BaseRepositoryCustom<TaskDataChange> {

  /**
   * Gets form constraints for task data change operations.
   * 
   * @return configuration constraints for task forms
   */
  TaskDataChangeFormConstraints getFormConstraints();

}
