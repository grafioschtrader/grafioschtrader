package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
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

  /**
   * The next tasks a worker of a queue may start: in the given state, due, of one of the task types of the queue and
   * not already taken by another worker of the same queue, by priority and then by creation time.
   *
   * @param progressState    the state, normally waiting
   * @param now              tasks whose earliest start time is not after this are due
   * @param idTasks          the task type values of the queue; must not be empty
   * @param claimed          the tasks other workers of the queue hold; must not be empty, pass a non-existing ID
   * @param pageable         normally the first entry only
   * @return the candidates in order
   */
  @Query("""
      SELECT t FROM TaskDataChange t WHERE t.progressStateType = ?1 AND t.earliestStartTime <= ?2 AND t.idTask IN ?3
      AND t.idTaskDataChange NOT IN ?4 ORDER BY t.executionPriority ASC, t.creationTime ASC""")
  List<TaskDataChange> findNextInQueue(byte progressState, LocalDateTime now, List<Byte> idTasks,
      List<Integer> claimed, Pageable pageable);

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

  /**
   * Like {@link #existsByIdTaskAndIdEntityAndProgressStateType(byte, Integer, byte)}, but also compares the entity
   * name, for a task type whose entity ID may refer to different entities. Null values match tasks without them.
   *
   * @param idTask            the task type value
   * @param entity            the simple class name of the entity, or null for the tasks without an entity
   * @param idEntity          the entity the task is for, or null for the tasks without an entity
   * @param progressStateType the progress state, normally waiting
   * @return true when at least one such task exists
   */
  boolean existsByIdTaskAndEntityAndIdEntityAndProgressStateType(byte idTask, String entity, Integer idEntity,
      byte progressStateType);

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
