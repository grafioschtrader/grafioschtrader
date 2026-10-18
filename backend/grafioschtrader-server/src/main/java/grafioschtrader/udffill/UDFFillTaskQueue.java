package grafioschtrader.udffill;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Enqueues fill runs of user-defined security fields. One task per field lets a task of higher priority, such as the
 * end-of-day price load, run between two fields instead of waiting for all of them.
 */
@Component
public class UDFFillTaskQueue {

  private final TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  public UDFFillTaskQueue(TaskDataChangeJpaRepository taskDataChangeJpaRepository) {
    this.taskDataChangeJpaRepository = taskDataChangeJpaRepository;
  }

  /**
   * Enqueues the run over all fields, unless one is already waiting. It recreates the fields of user 0 and enqueues one
   * run per user field.
   *
   * @param priority the priority of the run
   */
  public void enqueueAll(TaskDataExecPriority priority) {
    // A null entity is queried as IS NULL by the derived query
    if (!taskDataChangeJpaRepository.existsByIdTaskAndIdEntityAndProgressStateType(
        TaskTypeExtended.UDF_FILL_VALUES.getValue(), null, ProgressStateType.PROG_WAITING.getValue())) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.UDF_FILL_VALUES, priority));
    }
  }

  /**
   * Enqueues a fill run for one field, unless one for this field is already waiting.
   *
   * @param idUDFMetadata the field
   * @param priority      the priority of the run
   */
  public void enqueue(Integer idUDFMetadata, TaskDataExecPriority priority) {
    if (!isWaiting(idUDFMetadata)) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.UDF_FILL_VALUES, priority,
          LocalDateTime.now(), idUDFMetadata, UDFMetadataSecurity.class.getSimpleName()));
    }
  }

  /**
   * Enqueues a value proposal run of a language model for one field, unless one for this field is already waiting.
   *
   * @param idUDFMetadata the field
   * @param priority      the priority of the run
   */
  public void enqueueSuggest(Integer idUDFMetadata, TaskDataExecPriority priority) {
    // The entity ID of this task type may also be a tenant, so the entity name takes part in the comparison
    String entity = UDFMetadataSecurity.class.getSimpleName();
    if (!taskDataChangeJpaRepository.existsByIdTaskAndEntityAndIdEntityAndProgressStateType(
        TaskTypeExtended.UDF_USER_SUGGEST_VALUES.getValue(), entity, idUDFMetadata,
        ProgressStateType.PROG_WAITING.getValue())) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.UDF_USER_SUGGEST_VALUES, priority,
          LocalDateTime.now(), idUDFMetadata, entity));
    }
  }

  /**
   * Enqueues the value proposal run over all tenants, unless one is already waiting. It enqueues one run per language
   * model field with a fill period.
   *
   * @param priority the priority of the run
   */
  public void enqueueSuggestAll(TaskDataExecPriority priority) {
    // Null entity and entity ID are queried as IS NULL by the derived query
    if (!taskDataChangeJpaRepository.existsByIdTaskAndEntityAndIdEntityAndProgressStateType(
        TaskTypeExtended.UDF_USER_SUGGEST_VALUES.getValue(), null, null, ProgressStateType.PROG_WAITING.getValue())) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.UDF_USER_SUGGEST_VALUES, priority));
    }
  }

  /**
   * Tells whether a fill run for the field waits in the task queue.
   */
  public boolean isWaiting(Integer idUDFMetadata) {
    return taskDataChangeJpaRepository.existsByIdTaskAndIdEntityAndProgressStateType(
        TaskTypeExtended.UDF_FILL_VALUES.getValue(), idUDFMetadata, ProgressStateType.PROG_WAITING.getValue());
  }

  /**
   * Tells whether a fill run for the field executes now.
   */
  public boolean isRunning(Integer idUDFMetadata) {
    return taskDataChangeJpaRepository.existsByIdTaskAndIdEntityAndProgressStateType(
        TaskTypeExtended.UDF_FILL_VALUES.getValue(), idUDFMetadata, ProgressStateType.PROG_RUNNING.getValue());
  }
}
