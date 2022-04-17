package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.rest.UpdateCreateJpaRepository;

@Repository
public interface TaskDataChangeJpaRepository extends JpaRepository<TaskDataChange, Integer>,
    TaskDataChangeJpaRepositoryCustom, UpdateCreateJpaRepository<TaskDataChange> {

  Optional<TaskDataChange> findTopByProgressStateTypeAndEarliestStartTimeLessThanEqualOrderByExecutionPriorityAscCreationTimeAsc(
      byte progressState, LocalDateTime earliestStartTime);

  Optional<TaskDataChange> findByIdTaskAndIdEntityAndProgressStateType(byte idTask, Integer idEntity,
      byte progressStateType);

  @Transactional
  long removeByIdTaskDataChangeAndProgressStateTypeNot(Integer idTaskDataChange, byte progressStateType);
  
  long removeByIdTask(byte idTask);
  
  long removeByExecEndTimeBefore(LocalDateTime dateTime);
}
