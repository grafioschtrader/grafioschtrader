package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.TaskDataChange;
import grafiosch.rest.UpdateCreateJpaRepository;
import jakarta.transaction.Transactional;

public interface TaskDataChangeJpaRepository extends JpaRepository<TaskDataChange, Integer>,
    TaskDataChangeJpaRepositoryCustom, UpdateCreateJpaRepository<TaskDataChange> {

  /**
   * Finds all task data changes where the idTask is in the provided list. Used for filtering tasks by selected task
   * types.
   *
   * @param idTasks list of task type IDs to filter by
   * @return list of matching TaskDataChange entities
   */
  List<TaskDataChange> findByIdTaskIn(List<Byte> idTasks);

  Optional<TaskDataChange> findTopByProgressStateTypeAndEarliestStartTimeLessThanEqualOrderByExecutionPriorityAscCreationTimeAsc(
      byte progressState, LocalDateTime earliestStartTime);

  Optional<TaskDataChange> findByIdTaskAndIdEntityAndProgressStateType(byte idTask, Integer idEntity,
      byte progressStateType);

  /**
   * Whether a task of the given type for the given entity is in the given state. Unlike
   * {@link #findByIdTaskAndIdEntityAndProgressStateType(byte, Integer, byte)} it does not fail when several such tasks
   * exist, which concurrent enqueuing can produce. A null {@code idEntity} matches the tasks without an entity.
   *
   * @param idTask            the task type value
   * @param idEntity          the entity the task is for, or null for the tasks without an entity
   * @param progressStateType the progress state, normally waiting
   * @return true when at least one such task exists
   */
  boolean existsByIdTaskAndIdEntityAndProgressStateType(byte idTask, Integer idEntity, byte progressStateType);

  @Transactional
  void removeByIdTaskDataChangeAndProgressStateTypeNot(Integer idTaskDataChange, byte progressStateType);

  @Transactional
  void removeByIdTaskAndProgressStateType(byte idTask, byte progressStateType);

  boolean existsByIdTaskAndProgressStateType(byte idTask, byte progressStateType);

  long removeByExecEndTimeBefore(LocalDateTime dateTime);

  @Modifying
  @Transactional
  @Query("UPDATE TaskDataChange t SET t.progressStateType = ?2 WHERE t.progressStateType = ?1")
  int changeFromToProgressState(byte fromState, byte toState);

}
