package grafiosch.task;

/**
 * A queue of background tasks with its own workers. A task names its queue with {@link ITask#getQueue()}; every task
 * type of a queue runs only there, so a long task of one queue never holds up the tasks of another. The library defines
 * the main queue with one worker, which keeps its tasks strictly one after the other. An application adds a queue by
 * declaring a bean of this type, for example for tasks that mostly wait on a remote service and may therefore run in
 * parallel.
 *
 * @param name     the name a task returns from {@link ITask#getQueue()}
 * @param parallel how many tasks of this queue run at the same time, at least 1
 * @param labelKey the text key of the queue in the task monitor
 */
public record TaskQueueDefinition(String name, int parallel, String labelKey) {

  /** The queue of every task that names no other one. */
  public static final String MAIN = "MAIN";

  /** The main queue: one worker, labelled like the task monitor itself. */
  public static final TaskQueueDefinition MAIN_QUEUE = new TaskQueueDefinition(MAIN, 1, "TASK_DATA_MONITOR");

  public TaskQueueDefinition {
    parallel = Math.max(1, parallel);
  }
}
