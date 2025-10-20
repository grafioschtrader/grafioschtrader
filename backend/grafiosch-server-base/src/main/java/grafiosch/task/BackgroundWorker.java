package grafiosch.task;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import grafiosch.BaseConstants;
import grafiosch.alert.AlertBaseType;
import grafiosch.alert.AlertEvent;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.exceptions.TaskInterruptException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;

/**
 * One thread is started, which sequentially starts another thread per task. For a task a timeout can be set, this
 * preferably with threads with the possibility of an endless run exists. This continues to run and becomes a zombie but
 * does not hinder the start of new tasks.
 *
 */
@Component
public class BackgroundWorker implements DisposableBean, Runnable, ApplicationListener<ApplicationReadyEvent> {

  /** Wait time in milliseconds after a timeout occurs before checking thread status */
  private static long WAIT_MILISECONDS_AFTER_TIMEOUT = 10000;
  /** Polling interval in seconds for checking new tasks */
  private static long POLLING_TIME_SECONDS = 15;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired(required = false)
  private List<ITask> tasks = new ArrayList<>();

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** Main background thread that polls for tasks */
  private Thread backgroundThread;

  /** Worker thread that executes individual tasks */
  private Thread workerThread;

  /** Flag indicating if the main loop should continue running */
  private volatile boolean runningLoop;

  /** Information about the currently running task */
  private RunningTask runningTask;

  /** Flag indicating if the current task has timed out */
  private boolean timeout;

  BackgroundWorker() {
    backgroundThread = new Thread(this);
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    cleanUpZombieProcessAtStartUp();
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
          timeoutProcessRunningThread(taskDataChange);
        }
        TimeUnit.SECONDS.sleep(POLLING_TIME_SECONDS);
      } catch (InterruptedException ie) {
        log.warn("Backgroud thread was interrupted, Failed to complete operation");
      }
    }
  }

  /**
   * Cleans up zombie and running processes at application startup. Changes zombie processes to cleaned state and
   * running processes to zombie cleaned state.
   */
  private void cleanUpZombieProcessAtStartUp() {
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_ZOMBIE.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_RUNNING.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
  }

  /**
   * Executes a task with timeout handling. Creates a worker thread to run the task and monitors for timeout conditions.
   * If timeout occurs, attempts to interrupt the thread and handles zombie processes.
   * 
   * @param taskDataChange the task to execute
   * @throws InterruptedException if the thread is interrupted
   */
  private void timeoutProcessRunningThread(final TaskDataChange taskDataChange) throws InterruptedException {
    final LocalDateTime startTime = LocalDateTime.now();
    Optional<ITask> taskOpt = tasks.stream().filter(task -> task.getTaskType() == taskDataChange.getIdTask())
        .findFirst();
    if (taskOpt.isPresent()) {
      timeout = false;
      workerThread = new Thread(() -> executeJob(taskOpt.get(), taskDataChange, startTime));
      workerThread.start();
      workerThread.join(taskOpt.get().getTimeoutInSeconds() * 1000);
      if (workerThread.isAlive()) {
        timeout = true;
        workerThread.interrupt();
        Thread.sleep(WAIT_MILISECONDS_AFTER_TIMEOUT);
        if (workerThread.isAlive()) {
          finishedJob(taskDataChange, startTime, ProgressStateType.PROG_ZOMBIE);
          applicationEventPublisher.publishEvent(new AlertEvent(this, AlertBaseType.ALERT_GET_ZOMBIE_BACKGROUND_JOB,
              taskDataChange.getIdTaskDataChange()));
        }
      }
    }
  }

  /**
   * Executes the actual task logic with error handling. Handles task interruption, background exceptions, and general
   * exceptions. Updates task progress state and manages transaction rollback when needed.
   * 
   * @param task           the task implementation to execute
   * @param taskDataChange the task data
   * @param startTime      the execution start time
   */
  private void executeJob(final ITask task, TaskDataChange taskDataChange, LocalDateTime startTime) {
    try {
      runningTask = new RunningTask(task, taskDataChange);
      taskDataChange = startJob(taskDataChange, startTime);
      task.doWork(cloneTaskDataChange(taskDataChange));
      finishedJob(taskDataChange, startTime, ProgressStateType.PROG_PROCESSED);
      removeOtherSamePendingJobs(task);
    } catch (TaskInterruptException tie) {
      finishedJob(taskDataChange, startTime,
          timeout ? ProgressStateType.PROG_TIMEOUT : ProgressStateType.PROG_INTERRUPTED);
    } catch (TaskBackgroundException tbe) {
      if (tbe.getErrorMsgOfSystem() != null) {
        StringBuilder failure = new StringBuilder("");
        tbe.getErrorMsgOfSystem().forEach(m -> failure.append(m.toString() + BaseConstants.NEW_LINE));
        taskDataChange.setFailedStackTrace(
            failure.toString().substring(0, Math.min(failure.length(), TaskDataChange.MAX_SIZE_FAILED_STRACK_TRACE)));
      }
      taskDataChange.setFailedMessageCode(tbe.getErrorMessagesKey());
      if (tbe.isRollback()) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      }
      finishedJob(taskDataChange, startTime, ProgressStateType.PROG_FAILED);
    } catch (Exception e) {
      StringWriter errors = new StringWriter();
      e.printStackTrace(new PrintWriter(errors));
      taskDataChange.setFailedStackTrace(errors.toString().substring(0,
          Math.min(TaskDataChange.MAX_SIZE_FAILED_STRACK_TRACE, errors.toString().length())));
      finishedJob(taskDataChange, startTime, ProgressStateType.PROG_FAILED);
    } finally {
      runningTask = null;
    }
  }

  /**
   * Creates a clone of the task data change object.
   * 
   * @param taskDataChange the original task data change
   * @return cloned task data change object
   * @throws IllegalAccessException if bean property access fails
   * @throws InvocationTargetException if bean property copying fails
   */
  private TaskDataChange cloneTaskDataChange(TaskDataChange taskDataChange)
      throws IllegalAccessException, InvocationTargetException {
    TaskDataChange tdcNew = new TaskDataChange();
    BeanUtils.copyProperties(tdcNew, taskDataChange);
    return tdcNew;
  }

  /**
   * Marks a task as started and updates its state.
   * 
   * @param taskDataChange the task to start
   * @param startTime      the start time
   * @return the updated task data change
   */
  private TaskDataChange startJob(final TaskDataChange taskDataChange, LocalDateTime startTime) {
    taskDataChange.setExecStartTime(startTime);
    taskDataChange.setProgressStateType(ProgressStateType.PROG_RUNNING);
    return taskDataChangeRepository.save(taskDataChange);
  }

  /**
   * Marks a task as finished with the specified state.
   * 
   * @param taskDataChange    the task to finish
   * @param startTime         the start time
   * @param progressStateType the final progress state
   */
  private void finishedJob(final TaskDataChange taskDataChange, LocalDateTime startTime,
      ProgressStateType progressStateType) {
    taskDataChange.finishedJob(startTime, progressStateType);
    taskDataChangeRepository.save(taskDataChange);
  }

  /**
   * Removes other waiting jobs of the same task type if configured to do so.
   * 
   * @param task the task that was executed
   */
  public void removeOtherSamePendingJobs(final ITask task) {
    if (task.removeAllOtherPendingJobsOfSameTask()) {
      taskDataChangeRepository.removeByIdTaskAndProgressStateType(task.getTaskType().getValue(),
          ProgressStateType.PROG_WAITING.getValue());
    }
  }

  /**
   * Shuts down the background worker. Stops the main loop and interrupts any running job.
   */
  @Override
  public void destroy() {
    runningLoop = false;
    interruptingRunningJob(null);
  }

  /**
   * Interrupts a running job if it can be interrupted.
   * 
   * @param idTaskDataChange the ID of the task to interrupt, or null to interrupt any running task
   * @return true if the job was interrupted, false otherwise
   */
  public boolean interruptingRunningJob(Integer idTaskDataChange) {
    if (workerThread != null && workerThread.isAlive()
        && (idTaskDataChange == null || runningTask != null && runningTask.taskType.canBeInterrupted()
            && runningTask.taskDataChange.getIdTaskDataChange().equals(idTaskDataChange))) {
      workerThread.interrupt();
      return workerThread.isInterrupted();
    }
    return false;
  }

  /**
   * Holds information about a currently executing task. Used to track the running task type and associated data during
   * execution.
   */
  private static class RunningTask {
    /** The task implementation being executed */
    public ITask taskType;
    /** The task data being processed */
    public TaskDataChange taskDataChange;

    public RunningTask(ITask taskType, TaskDataChange taskDataChange) {
      this.taskType = taskType;
      this.taskDataChange = taskDataChange;
    }

  }

}