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
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.service.AlgoAlarmEvaluationService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Scheduled task for Tier 2 alarm evaluation: indicator-based alerts (MA crossing, RSI threshold, EvalEx expression).
 * Triggers on cron schedule configured via {@code gt.algo.alarm.indicator.evaluation} property. Before evaluation,
 * securities with stale prices (>4 hours old) are refreshed.
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

  @Scheduled(cron = "${gt.algo.alarm.indicator.evaluation}", zone = BaseConstants.TIME_ZONE)
  public void triggerAlgoAlarmIndicatorEvaluation() {
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
