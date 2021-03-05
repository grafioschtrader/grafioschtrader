package grafioschtrader.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.types.ProgressStateType;

@Component
class BackgroundWorker implements DisposableBean, Runnable, ApplicationListener<ApplicationReadyEvent> {

  @Autowired
  TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired(required = false)
  public List<ITask> tasks = new ArrayList<>();

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
                ProgressStateType.WAITING.getValue(), LocalDateTime.now());
        if (taskDataChangeOpt.isPresent()) {
          final TaskDataChange taskDataChange = taskDataChangeOpt.get();
          LocalDateTime startTime = LocalDateTime.now();
          tasks.stream().filter(task -> task.getTaskType() == taskDataChange.getTaskType()).findFirst().ifPresentOrElse(task -> {
            executeJob(task, taskDataChange, startTime);
          }, () -> finishedJob(taskDataChange, startTime, ProgressStateType.TASK_NOT_FOUND));
         
        }
        TimeUnit.SECONDS.sleep(15);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
  
  private void executeJob(ITask task, final TaskDataChange taskDataChange, LocalDateTime startTime) {
    try {
      task.doWork(taskDataChange.getIdEntity(), taskDataChange.getEntity());
      finishedJob(taskDataChange, startTime, ProgressStateType.PROCESSED);
      if(task.removeAllOtherJobsOfSameTask()) {
        taskDataChangeRepository.removeByIdTask(task.getTaskType().getValue());
      }
    } catch (TaskBackgroundException tbe) {
      System.out.println("=================================== - TaskBackgroundException");
      if(tbe.getErrorMsgOfSystem() != null) {
        tbe.getErrorMsgOfSystem().forEach(System.out::println);
      }
      finishedJob(taskDataChange, startTime, ProgressStateType.FAILED);
    } catch (Exception e) {
      System.out.println("=================================== - Exception");
      e.printStackTrace();
      finishedJob(taskDataChange, startTime, ProgressStateType.FAILED);
    }
  }
  

  private void finishedJob(final TaskDataChange taskDataChange, LocalDateTime startTime, ProgressStateType progressStateType) {
    taskDataChange.finishedJob(startTime, progressStateType);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void destroy() {
    someCondition = false;
  }

  

}