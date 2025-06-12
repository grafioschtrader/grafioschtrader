package grafiosch.task;

import java.util.List;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.types.ITaskType;

/**
 * Interface for background task implementations.
 * 
 * <p>
 * Defines the contract for all background tasks that can be executed by the task processing system. Implementations
 * handle specific business logic for different types of background operations.
 * </p>
 */
public interface ITask {

  /**
   * Gets the task type identifier.
   * 
   * @return the task type this implementation handles
   */
  ITaskType getTaskType();

  /**
   * Executes the main task logic.
   * 
   * @param taskDataChange the task data containing execution parameters
   * @throws TaskBackgroundException if task execution fails
   */
  void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException;

  /**
   * Indicates whether other pending jobs of the same task type should be removed when this task is scheduled.
   * 
   * @return true if other jobs should be removed, false otherwise
   */
  default boolean removeAllOtherJobsOfSameTask() {
    return false;
  }

  /**
   * Gets the list of entity types this task is allowed to process.
   * 
   * @return list of allowed entity names, or null if no restrictions
   */
  default List<String> getAllowedEntities() {
    return null;
  }

  /**
   * Indicates whether this task can be safely interrupted during execution.
   * 
   * @return true if task supports interruption, false otherwise
   */
  default boolean canBeInterrupted() {
    return false;
  }

  /**
   * Gets the maximum execution time for this task.
   * 
   * @return timeout in seconds, or 0 if no timeout is set
   */
  default long getTimeoutInSeconds() {
    return 0L;
  }

}
