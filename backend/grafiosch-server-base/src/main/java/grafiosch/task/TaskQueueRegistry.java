package grafiosch.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Assigns every task type to a queue. The main queue comes first and holds every task whose queue is not declared as a
 * {@link TaskQueueDefinition} bean, so a misconfigured task still runs, only not in parallel.
 */
@Component
public class TaskQueueRegistry {

  private final List<TaskQueueDefinition> queues = new ArrayList<>();
  private final Map<String, List<Byte>> taskTypesByQueue = new LinkedHashMap<>();
  private final Map<Byte, String> queueByTaskType = new LinkedHashMap<>();


  public TaskQueueRegistry(@Autowired(required = false) List<ITask> tasks,
      @Autowired(required = false) List<TaskQueueDefinition> declared) {
    queues.add(TaskQueueDefinition.MAIN_QUEUE);
    if (declared != null) {
      declared.stream().filter(q -> !TaskQueueDefinition.MAIN.equals(q.name())).forEach(queues::add);
    }
    queues.forEach(q -> taskTypesByQueue.put(q.name(), new ArrayList<>()));
    if (tasks != null) {
      for (ITask task : tasks) {
        String queue = taskTypesByQueue.containsKey(task.getQueue()) ? task.getQueue() : TaskQueueDefinition.MAIN;
        byte type = task.getTaskType().getValue();
        taskTypesByQueue.get(queue).add(type);
        queueByTaskType.put(type, queue);
      }
    }
  }

  /** The queues, the main queue first. */
  public List<TaskQueueDefinition> getQueues() {
    return List.copyOf(queues);
  }

  /**
   * The values of the task types that run in a queue.
   *
   * @param queue the name of the queue
   * @return the task type values, empty for an unknown queue
   */
  public List<Byte> getTaskTypes(String queue) {
    return List.copyOf(taskTypesByQueue.getOrDefault(queue, List.of()));
  }

  /** The queue of every task type, by its value. */
  public Map<Byte, String> getQueueByTaskType() {
    return Map.copyOf(queueByTaskType);
  }
}
