package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.TaskDataChange;

public interface TaskDataChangeJpaRepository
    extends JpaRepository<TaskDataChange, Integer>, TaskDataChangeJpaRepositoryCustom {

  Optional<TaskDataChange> findTopByProgressStateTypeAndEarliestStartTimeLessThanEqualOrderByExecutionPriorityAscCreationTimeAsc(
      byte progressState, LocalDateTime earliestStartTime);

  Optional<TaskDataChange> findByIdTaskAndIdEntityAndProgressStateType(byte idTask, Integer idEntity,
      byte progressStateType);

  long removeByIdTask(byte idTask);

}
