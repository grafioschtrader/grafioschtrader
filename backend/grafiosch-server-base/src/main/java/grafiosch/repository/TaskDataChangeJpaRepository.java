package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.TaskDataChange;
import grafiosch.rest.UpdateCreateJpaRepository;
import jakarta.transaction.Transactional;

public interface TaskDataChangeJpaRepository extends JpaRepository<TaskDataChange, Integer>,
    TaskDataChangeJpaRepositoryCustom, UpdateCreateJpaRepository<TaskDataChange> {

  Optional<TaskDataChange> findTopByProgressStateTypeAndEarliestStartTimeLessThanEqualOrderByExecutionPriorityAscCreationTimeAsc(
      byte progressState, LocalDateTime earliestStartTime);

  Optional<TaskDataChange> findByIdTaskAndIdEntityAndProgressStateType(byte idTask, Integer idEntity,
      byte progressStateType);

  @Transactional
  long removeByIdTaskDataChangeAndProgressStateTypeNot(Integer idTaskDataChange, byte progressStateType);

  @Transactional
  long removeByIdTaskAndProgressStateType(byte idTask, byte progressStateType);

  long removeByExecEndTimeBefore(LocalDateTime dateTime);

  @Modifying
  @Transactional
  @Query("UPDATE TaskDataChange t SET t.progressStateType = ?2 WHERE t.progressStateType = ?1")
  int changeFromToProgressState(byte fromState, byte toState);

}
