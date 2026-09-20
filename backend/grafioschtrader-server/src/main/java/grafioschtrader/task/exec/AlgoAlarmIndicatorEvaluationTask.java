package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.service.AlgoAlarmEvaluationService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Five-minute eligibility scan for all rule based alerts. The configured GlobalParameters interval controls actual
 * work; the existing queue worker executes at most one pending/running task. Task ID 50 is retained for compatibility.
 */
@Component
public class AlgoAlarmIndicatorEvaluationTask implements ITask {

  @Autowired
  private AlgoAlarmEvaluationService algoAlarmEvaluationService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.ALGO_ALARM_INDICATOR_EVALUATION;
  }

  @Scheduled(cron = "${gt.algo.alarm.evaluation.scan:0 */5 * * * ?}", zone = BaseConstants.TIME_ZONE)
  public synchronized void triggerAlgoAlarmIndicatorEvaluation() {
    if (taskDataChangeRepository.existsByIdTaskAndProgressStateType(getTaskType().getValue(),
        ProgressStateType.PROG_WAITING.getValue())
        || taskDataChangeRepository.existsByIdTaskAndProgressStateType(getTaskType().getValue(),
            ProgressStateType.PROG_RUNNING.getValue())
        || !algoAlarmEvaluationService.hasDueAlerts())
      return;
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    algoAlarmEvaluationService.evaluateIndicatorAlerts();
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
