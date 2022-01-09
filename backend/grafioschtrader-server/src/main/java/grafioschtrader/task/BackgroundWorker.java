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
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.types.ProgressStateType;

@Component
class BackgroundWorker implements DisposableBean, Runnable, ApplicationListener<ApplicationReadyEvent> {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired(required = false)
  private List<ITask> tasks = new ArrayList<>();

  private Thread thread;
  private volatile boolean someCondition;

  BackgroundWorker() {
    this.thread = new Thread(this);
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    someCondition = true;
    this.thread.start();
  }

  @Override
  public void run() {
    while (someCondition) {
      try {
        Optional<TaskDataChange> taskDataChangeOpt = taskDataChangeRepository
            .findTopByProgressStateTypeAndEarliestStartTimeLessThanEqualOrderByExecutionPriorityAscCreationTimeAsc(
                ProgressStateType.PROG_WAITING.getValue(), LocalDateTime.now());
        if (taskDataChangeOpt.isPresent()) {
          final TaskDataChange taskDataChange = taskDataChangeOpt.get();
          LocalDateTime startTime = LocalDateTime.now();
          tasks.stream().filter(task -> task.getTaskType() == taskDataChange.getIdTask()).findFirst()
              .ifPresentOrElse(task -> {
                executeJob(task, taskDataChange, startTime);
              }, () -> finishedJob(taskDataChange, startTime, ProgressStateType.PROG_TASK_NOT_FOUND));

        }
        TimeUnit.SECONDS.sleep(15);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private void executeJob(final ITask task, TaskDataChange taskDataChange, LocalDateTime startTime) {
    try {
      taskDataChange = startJob(taskDataChange, startTime);
      task.doWork(cloneTaskDataChange(taskDataChange));
      finishedJob(taskDataChange, startTime, ProgressStateType.PROG_PROCESSED);
      if (task.removeAllOtherJobsOfSameTask()) {
        taskDataChangeRepository.removeByIdTask(task.getTaskType().getValue());
      }
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
    someCondition = false;
  }

}