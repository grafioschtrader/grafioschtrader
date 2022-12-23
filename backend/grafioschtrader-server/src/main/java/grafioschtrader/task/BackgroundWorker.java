package grafioschtrader.task;

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

import grafioschtrader.alert.AlertEvent;
import grafioschtrader.alert.AlertType;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.exceptions.TaskInterruptException;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.types.ProgressStateType;

/**
 * One thread is started, which sequentially starts another thread per task. For
 * a task a timeout can be set, this preferably with threads with the
 * possibility of an endless run exists. This continues to run and becomes a
 * zombie but does not hinder the start of new tasks.
 *
 */
@Component
public class BackgroundWorker implements DisposableBean, Runnable, ApplicationListener<ApplicationReadyEvent> {

  private static long WAIT_MILISECONDS_AFTER_TIMEOUT = 10000;
  private static long POLLING_TIME_SECONDS = 15;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired(required = false)
  private List<ITask> tasks = new ArrayList<>();

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private Thread backgroundThread;
  private Thread workerThread;
  private volatile boolean runningLoop;
  private RunningTask runningTask;
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

  private void cleanUpZombieProcessAtStartUp() {
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_ZOMBIE.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
    taskDataChangeRepository.changeFromToProgressState(ProgressStateType.PROG_RUNNING.getValue(),
        ProgressStateType.PROG_ZOMBIE_CLEANED.getValue());
  }

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
          applicationEventPublisher.publishEvent(
              new AlertEvent(this, AlertType.ALERT_GET_ZOMBIE_BACKGROUND_JOB, taskDataChange.getIdTaskDataChange()));
        }
      }
    }
  }

  private void executeJob(final ITask task, TaskDataChange taskDataChange, LocalDateTime startTime) {
    try {
      runningTask = new RunningTask(task, taskDataChange);
      taskDataChange = startJob(taskDataChange, startTime);
      task.doWork(cloneTaskDataChange(taskDataChange));
      finishedJob(taskDataChange, startTime, ProgressStateType.PROG_PROCESSED);
      if (task.removeAllOtherJobsOfSameTask()) {
        taskDataChangeRepository.removeByIdTask(task.getTaskType().getValue());
      }
    } catch (TaskInterruptException tie) {
      finishedJob(taskDataChange, startTime,
          timeout ? ProgressStateType.PROG_TIMEOUT : ProgressStateType.PROG_INTERRUPTED);
    } catch (TaskBackgroundException tbe) {
      if (tbe.getErrorMsgOfSystem() != null) {
        StringBuilder failure = new StringBuilder("");
        tbe.getErrorMsgOfSystem().forEach(m -> failure.append(m.toString() + "\n"));
        taskDataChange
            .setFailedStackTrace(failure.toString().substring(0, TaskDataChange.MAX_SIZE_FAILED_STRACK_TRACE));
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
    TaskDataChange tdcNew = new TaskDataChange();
    BeanUtils.copyProperties(tdcNew, taskDataChange);
    return tdcNew;
  }

  private TaskDataChange startJob(final TaskDataChange taskDataChange, LocalDateTime startTime) {
    taskDataChange.setExecStartTime(startTime);
    taskDataChange.setProgressStateType(ProgressStateType.PROG_RUNNING);
    return taskDataChangeRepository.save(taskDataChange);
  }

  private void finishedJob(final TaskDataChange taskDataChange, LocalDateTime startTime,
      ProgressStateType progressStateType) {
    taskDataChange.finishedJob(startTime, progressStateType);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void destroy() {
    runningLoop = false;
    interruptingRunningJob(null);
  }

  public boolean interruptingRunningJob(Integer idTaskDataChange) {
    if (workerThread != null && workerThread.isAlive()
        && (idTaskDataChange == null || runningTask != null && runningTask.taskType.canBeInterrupted()
            && runningTask.taskDataChange.getIdTaskDataChange().equals(idTaskDataChange))) {
      workerThread.interrupt();
      return workerThread.isInterrupted();
    }
    return false;
  }

  private static class RunningTask {
    public ITask taskType;
    public TaskDataChange taskDataChange;

    public RunningTask(ITask taskType, TaskDataChange taskDataChange) {
      this.taskType = taskType;
      this.taskDataChange = taskDataChange;
    }

  }

}