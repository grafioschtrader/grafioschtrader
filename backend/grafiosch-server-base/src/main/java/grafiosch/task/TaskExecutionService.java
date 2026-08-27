package grafiosch.task;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

  private volatile Thread workerThread;
  private volatile RunningTask runningTask;
  private volatile boolean timeout;

  public void cleanUpZombieProcessAtStartUp() {
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_ZOMBIE.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_RUNNING.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
  }

  /** Executes the persisted task with the timeout declared by its {@link ITask} implementation. */
  public synchronized TaskDataChange executeWithTimeout(Integer idTaskDataChange) throws InterruptedException {
    TaskDataChange taskDataChange = taskDataChangeRepository.findById(idTaskDataChange).orElseThrow();
    executeWithTimeout(taskDataChange);
    return taskDataChangeRepository.findById(idTaskDataChange).orElseThrow();
  }

  public synchronized void executeWithTimeout(TaskDataChange taskDataChange) throws InterruptedException {
    LocalDateTime startTime = LocalDateTime.now();
    Optional<ITask> taskOpt = tasks.stream().filter(task -> task.getTaskType() == taskDataChange.getIdTask())
        .findFirst();
    if (taskOpt.isEmpty()) {
      throw new IllegalArgumentException("No task implementation for task type " + taskDataChange.getIdTask());
    }
    ITask task = taskOpt.get();
    timeout = false;
    workerThread = new Thread(() -> executeJob(task, taskDataChange, startTime));
    workerThread.start();
    workerThread.join(task.getTimeoutInSeconds() * 1000);
    if (workerThread.isAlive()) {
      timeout = true;
      workerThread.interrupt();
      Thread.sleep(WAIT_MILLISECONDS_AFTER_TIMEOUT);
      if (workerThread.isAlive()) {
        finishedJob(taskDataChange, startTime, ProgressStateType.PROG_ZOMBIE);
        applicationEventPublisher.publishEvent(
            new AlertEvent(this, AlertBaseType.ALERT_GET_ZOMBIE_BACKGROUND_JOB, taskDataChange.getIdTaskDataChange()));
      }
    }
  }

  private void executeJob(ITask task, TaskDataChange taskDataChange, LocalDateTime startTime) {
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
    } finally {
      runningTask = null;
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

  public boolean interruptingRunningJob(Integer idTaskDataChange) {
    Thread currentWorker = workerThread;
    RunningTask currentTask = runningTask;
    if (currentWorker != null && currentWorker.isAlive()
        && (idTaskDataChange == null || currentTask != null && currentTask.taskType.canBeInterrupted()
            && currentTask.taskDataChange.getIdTaskDataChange().equals(idTaskDataChange))) {
      currentWorker.interrupt();
      return currentWorker.isInterrupted();
    }
    return false;
  }

  private record RunningTask(ITask taskType, TaskDataChange taskDataChange) {
  }
}
