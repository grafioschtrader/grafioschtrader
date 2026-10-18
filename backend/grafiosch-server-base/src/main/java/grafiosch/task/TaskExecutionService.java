package grafiosch.task;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
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
 * Executes persisted background tasks independently of the polling worker. This separation lets controlled runtimes
 * disable polling while still using exactly the same task lifecycle from an explicit trigger.
 *
 * Several tasks may run at the same time, one per worker of each {@link TaskQueueDefinition}. Every running task
 * therefore keeps its own thread and timeout state, so a timeout, a zombie or an interruption concerns only that task.
 */
@Service
public class TaskExecutionService {

  private static final long WAIT_MILLISECONDS_AFTER_TIMEOUT = 10000;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired(required = false)
  private List<ITask> tasks = new ArrayList<>();

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  /** The tasks that execute now, by the ID of their {@link TaskDataChange}. */
  private final Map<Integer, RunningTask> runningTasks = new ConcurrentHashMap<>();

  public void cleanUpZombieProcessAtStartUp() {
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_ZOMBIE.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_RUNNING.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
  }

  /** Executes the persisted task with the timeout declared by its {@link ITask} implementation. */
  public TaskDataChange executeWithTimeout(Integer idTaskDataChange) throws InterruptedException {
    TaskDataChange taskDataChange = taskDataChangeRepository.findById(idTaskDataChange).orElseThrow();
    executeWithTimeout(taskDataChange);
    return taskDataChangeRepository.findById(idTaskDataChange).orElseThrow();
  }

  /**
   * Executes one task in its own thread and waits for it at most as long as its timeout. A task still alive after the
   * timeout is interrupted; if it does not stop, it is marked as zombie and an alert is raised. The calling worker
   * then goes on, the zombie keeps its thread.
   *
   * @param taskDataChange the waiting task
   * @throws InterruptedException when the calling worker is interrupted while it waits
   */
  public void executeWithTimeout(TaskDataChange taskDataChange) throws InterruptedException {
    LocalDateTime startTime = LocalDateTime.now();
    ITask task = findTask(taskDataChange).orElseThrow(
        () -> new IllegalArgumentException("No task implementation for task type " + taskDataChange.getIdTask()));
    RunningTask running = new RunningTask(task, taskDataChange);
    Thread worker = new Thread(() -> executeJob(running, startTime));
    running.thread = worker;
    runningTasks.put(taskDataChange.getIdTaskDataChange(), running);
    try {
      worker.start();
      worker.join(task.getTimeoutInSeconds() * 1000);
      if (worker.isAlive()) {
        running.timeout = true;
        worker.interrupt();
        Thread.sleep(WAIT_MILLISECONDS_AFTER_TIMEOUT);
        if (worker.isAlive()) {
          finishedJob(taskDataChange, startTime, ProgressStateType.PROG_ZOMBIE);
          applicationEventPublisher.publishEvent(new AlertEvent(this, AlertBaseType.ALERT_GET_ZOMBIE_BACKGROUND_JOB,
              taskDataChange.getIdTaskDataChange()));
        }
      }
    } finally {
      runningTasks.remove(taskDataChange.getIdTaskDataChange());
    }
  }

  /**
   * Returns the implementation of the type of a task.
   *
   * @param taskDataChange the task
   * @return the implementation, empty when none is registered
   */
  public Optional<ITask> findTask(TaskDataChange taskDataChange) {
    return tasks.stream().filter(task -> task.getTaskType() == taskDataChange.getIdTask()).findFirst();
  }

  private void executeJob(RunningTask running, LocalDateTime startTime) {
    ITask task = running.taskType;
    TaskDataChange taskDataChange = running.taskDataChange;
    try {
      taskDataChange = startJob(taskDataChange, startTime);
      task.doWork(cloneTaskDataChange(taskDataChange));
      finishedJob(taskDataChange, startTime, ProgressStateType.PROG_PROCESSED);
      removeOtherSamePendingJobs(task);
    } catch (TaskInterruptException tie) {
      finishedJob(taskDataChange, startTime,
          running.timeout ? ProgressStateType.PROG_TIMEOUT : ProgressStateType.PROG_INTERRUPTED);
    } catch (TaskBackgroundException tbe) {
      if (tbe.getErrorMsgOfSystem() != null) {
        StringBuilder failure = new StringBuilder();
        tbe.getErrorMsgOfSystem().forEach(message -> failure.append(message).append(BaseConstants.NEW_LINE));
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
    }
  }

  private TaskDataChange cloneTaskDataChange(TaskDataChange taskDataChange)
      throws IllegalAccessException, InvocationTargetException {
    TaskDataChange clone = new TaskDataChange();
    BeanUtils.copyProperties(clone, taskDataChange);
    return clone;
  }

  private TaskDataChange startJob(TaskDataChange taskDataChange, LocalDateTime startTime) {
    taskDataChange.setExecStartTime(startTime);
    taskDataChange.setProgressStateType(ProgressStateType.PROG_RUNNING);
    return taskDataChangeRepository.save(taskDataChange);
  }

  private void finishedJob(TaskDataChange taskDataChange, LocalDateTime startTime,
      ProgressStateType progressStateType) {
    taskDataChange.finishedJob(startTime, progressStateType);
    taskDataChangeRepository.save(taskDataChange);
  }

  public void removeOtherSamePendingJobs(ITask task) {
    if (task.removeAllOtherPendingJobsOfSameTask()) {
      taskDataChangeRepository.removeByIdTaskAndProgressStateType(task.getTaskType().getValue(),
          ProgressStateType.PROG_WAITING.getValue());
    }
  }

  /**
   * Interrupts a running task that allows it, or every running task.
   *
   * @param idTaskDataChange the task to interrupt, or null to interrupt every running task, as on shutdown
   * @return true when a thread was interrupted
   */
  public boolean interruptingRunningJob(Integer idTaskDataChange) {
    if (idTaskDataChange == null) {
      boolean interrupted = false;
      for (RunningTask running : runningTasks.values()) {
        interrupted |= interrupt(running);
      }
      return interrupted;
    }
    RunningTask running = runningTasks.get(idTaskDataChange);
    return running != null && running.taskType.canBeInterrupted() && interrupt(running);
  }

  private static boolean interrupt(RunningTask running) {
    Thread thread = running.thread;
    if (thread != null && thread.isAlive()) {
      thread.interrupt();
      return thread.isInterrupted();
    }
    return false;
  }

  /** One task in execution: its thread, and whether its timeout interrupted it. */
  private static final class RunningTask {
    private final ITask taskType;
    private final TaskDataChange taskDataChange;
    private volatile Thread thread;
    private volatile boolean timeout;

    private RunningTask(ITask taskType, TaskDataChange taskDataChange) {
      this.taskType = taskType;
      this.taskDataChange = taskDataChange;
    }
  }
}
