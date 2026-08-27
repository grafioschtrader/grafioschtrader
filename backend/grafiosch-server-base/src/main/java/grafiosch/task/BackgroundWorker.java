package grafiosch.task;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;

/**
 * One thread is started, which sequentially starts another thread per task. For a task a timeout can be set, this
 * preferably with threads with the possibility of an endless run exists. This continues to run and becomes a zombie but
 * does not hinder the start of new tasks.
 *
 */
@Component
@ConditionalOnProperty(name = "g.background.worker.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundWorker implements DisposableBean, Runnable, ApplicationListener<ApplicationReadyEvent> {

  /** Polling interval in seconds for checking new tasks. */
  private static final long POLLING_TIME_SECONDS = 15;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private TaskExecutionService taskExecutionService;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** Main background thread that polls for tasks */
  private Thread backgroundThread;

  /** Flag indicating if the main loop should continue running */
  private volatile boolean runningLoop;

  BackgroundWorker() {
    backgroundThread = new Thread(this);
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    taskExecutionService.cleanUpZombieProcessAtStartUp();
    runningLoop = true;
    backgroundThread.start();
  }

  @Override
  public void run() {
    while (runningLoop) {
      try {
        Optional<TaskDataChange> taskDataChangeOpt = taskDataChangeRepository
            .findTopByProgressStateTypeAndEarliestStartTimeLessThanEqualOrderByExecutionPriorityAscCreationTimeAsc(
                ProgressStateType.PROG_WAITING.getValue(), LocalDateTime.now());
        if (taskDataChangeOpt.isPresent()) {
          final TaskDataChange taskDataChange = taskDataChangeOpt.get();
          taskExecutionService.executeWithTimeout(taskDataChange);
        }
        TimeUnit.SECONDS.sleep(POLLING_TIME_SECONDS);
      } catch (InterruptedException ie) {
        log.warn("Backgroud thread was interrupted, Failed to complete operation");
      }
    }
  }

  /**
   * Shuts down the background worker. Stops the main loop and interrupts any running job.
   */
  @Override
  public void destroy() {
    runningLoop = false;
    backgroundThread.interrupt();
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
