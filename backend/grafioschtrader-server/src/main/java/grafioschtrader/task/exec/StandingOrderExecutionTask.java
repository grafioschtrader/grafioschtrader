package grafioschtrader.task.exec;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.service.StandingOrderExecutionService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Scheduled task that processes due standing orders daily. Queries all active standing orders whose
 * {@code nextExecutionDate} has arrived and creates corresponding {@link grafioschtrader.entities.Transaction}
 * entities via the existing transaction pipeline.
 */
@Component
public class StandingOrderExecutionTask implements ITask {

  @Autowired
  private StandingOrderExecutionService standingOrderExecutionService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.STANDING_ORDER_EXECUTION;
  }

  @Scheduled(cron = "${gt.standing.order.execution}", zone = BaseConstants.TIME_ZONE)
  public void triggerStandingOrderExecution() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    standingOrderExecutionService.executeAllDueStandingOrders(LocalDate.now());
  }

  @Override
  public boolean canBeInterrupted() {
    return true;
  }

  @Override
  public long getTimeoutInSeconds() {
    return 1800;
  }
}
