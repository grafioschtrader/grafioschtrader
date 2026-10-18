package grafiosch.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;

/**
 * Starts the workers of every {@link TaskQueueDefinition}: as many threads per queue as it may run in parallel. Each
 * worker takes the next due task of its queue that no other worker of the queue holds, and executes it in a thread of
 * its own with the timeout of the task. A task that does not stop after its timeout becomes a zombie but does not
 * hinder the start of new tasks. The main queue has one worker, so its tasks still run strictly one after the other, while a
 * long task of another queue no longer holds them up.
 */
@Component
@ConditionalOnProperty(name = "g.background.worker.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundWorker implements DisposableBean, ApplicationListener<ApplicationReadyEvent> {

  /** Polling interval in seconds for checking new tasks. */
  private static final long POLLING_TIME_SECONDS = 15;

  /** Stands for "no task claimed" in the query, which cannot take an empty list. */
  private static final List<Integer> NOTHING_CLAIMED = List.of(-1);

  /** Due tasks read per poll, so that one whose subject is busy does not block those behind it. */
  private static final int CANDIDATES = 20;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private TaskExecutionService taskExecutionService;

  @Autowired
  private TaskQueueRegistry taskQueueRegistry;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** The worker threads of all queues. */
  private final List<Thread> workers = new ArrayList<>();

  /** Flag indicating if the worker loops should continue running */
  private volatile boolean runningLoop;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    taskExecutionService.cleanUpZombieProcessAtStartUp();
    runningLoop = true;
    for (TaskQueueDefinition queue : taskQueueRegistry.getQueues()) {
      List<Byte> taskTypes = taskQueueRegistry.getTaskTypes(queue.name());
      if (taskTypes.isEmpty()) {
        continue;
      }
      Map<Integer, String> claimed = new HashMap<>();
      for (int i = 1; i <= queue.parallel(); i++) {
        Thread worker = new Thread(() -> runWorker(taskTypes, claimed), "task-" + queue.name() + "-" + i);
        workers.add(worker);
        worker.start();
      }
    }
  }

  /**
   * The loop of one worker of a queue.
   *
   * @param taskTypes the task type values of the queue
   * @param claimed   the tasks the workers of this queue hold with their subject, shared and guarded by itself
   */
  private void runWorker(List<Byte> taskTypes, Map<Integer, String> claimed) {
    while (runningLoop) {
      try {
        Optional<TaskDataChange> next = claimNext(taskTypes, claimed);
        if (next.isPresent()) {
          try {
            taskExecutionService.executeWithTimeout(next.get());
          } finally {
            synchronized (claimed) {
              claimed.remove(next.get().getIdTaskDataChange());
            }
          }
        }
        TimeUnit.SECONDS.sleep(POLLING_TIME_SECONDS);
      } catch (InterruptedException ie) {
        log.warn("Background worker {} was interrupted", Thread.currentThread().getName());
        if (!runningLoop) {
          return;
        }
      } catch (RuntimeException e) {
        // A failing query must not end the worker for good; the next poll tries again
        log.error("Background worker {} failed to take the next task", Thread.currentThread().getName(), e);
      }
    }
  }

  /**
   * Takes the next due task of the queue that no other worker of the queue holds. A task of the same type for the same
   * entity as a held one is skipped until that one ends, so two runs never work on the same subject at once. Querying
   * and claiming happen under one lock, so two workers of a queue never start the same task.
   */
  Optional<TaskDataChange> claimNext(List<Byte> taskTypes, Map<Integer, String> claimed) {
    synchronized (claimed) {
      List<TaskDataChange> candidates = taskDataChangeRepository.findNextInQueue(
          ProgressStateType.PROG_WAITING.getValue(), LocalDateTime.now(), taskTypes,
          claimed.isEmpty() ? NOTHING_CLAIMED : List.copyOf(claimed.keySet()), PageRequest.of(0, CANDIDATES));
      Optional<TaskDataChange> next = candidates.stream().filter(t -> !claimed.containsValue(subject(t))).findFirst();
      next.ifPresent(t -> claimed.put(t.getIdTaskDataChange(), subject(t)));
      return next;
    }
  }

  /**
   * What a task works on: its type with its entity, or for a task without entity the task itself, which then never
   * collides with another one.
   */
  static String subject(TaskDataChange taskDataChange) {
    return taskDataChange.getIdEntity() == null ? "#" + taskDataChange.getIdTaskDataChange()
        : taskDataChange.getIdTask().getValue() + "/" + taskDataChange.getEntity() + "/" + taskDataChange.getIdEntity();
  }

  /**
   * Shuts down the background workers. Stops the loops and interrupts every running job.
   */
  @Override
  public void destroy() {
    runningLoop = false;
    workers.forEach(Thread::interrupt);
    taskExecutionService.interruptingRunningJob(null);
  }

  /**
   * Interrupts a running job if it can be interrupted.
   *
   * @param idTaskDataChange the ID of the task to interrupt, or null to interrupt any running task
   * @return true if the job was interrupted, false otherwise
   */
  public boolean interruptingRunningJob(Integer idTaskDataChange) {
    return taskExecutionService.interruptingRunningJob(idTaskDataChange);
  }

}
