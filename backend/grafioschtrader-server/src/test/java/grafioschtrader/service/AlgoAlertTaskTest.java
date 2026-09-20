package grafioschtrader.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafioschtrader.task.exec.AlgoAlarmIndicatorEvaluationTask;

class AlgoAlertTaskTest {
  @Test
  void enqueuesOnlyDueWorkAndNeverDuplicatesPendingOrRunningTask() {
    AlgoAlarmEvaluationService evaluator = mock(AlgoAlarmEvaluationService.class);
    TaskDataChangeJpaRepository repository = mock(TaskDataChangeJpaRepository.class);
    AlgoAlarmIndicatorEvaluationTask task = new AlgoAlarmIndicatorEvaluationTask();
    ReflectionTestUtils.setField(task, "algoAlarmEvaluationService", evaluator);
    ReflectionTestUtils.setField(task, "taskDataChangeRepository", repository);
    task.triggerAlgoAlarmIndicatorEvaluation();
    verify(repository, never()).save(any(TaskDataChange.class));
    when(evaluator.hasDueAlerts()).thenReturn(true);
    task.triggerAlgoAlarmIndicatorEvaluation();
    verify(repository).save(any(TaskDataChange.class));
    clearInvocations(repository);
    when(repository.existsByIdTaskAndProgressStateType(anyByte(), eq(ProgressStateType.PROG_WAITING.getValue())))
        .thenReturn(true);
    task.triggerAlgoAlarmIndicatorEvaluation();
    verify(repository, never()).save(any(TaskDataChange.class));
    when(repository.existsByIdTaskAndProgressStateType(anyByte(), eq(ProgressStateType.PROG_WAITING.getValue())))
        .thenReturn(false);
    when(repository.existsByIdTaskAndProgressStateType(anyByte(), eq(ProgressStateType.PROG_RUNNING.getValue())))
        .thenReturn(true);
    task.triggerAlgoAlarmIndicatorEvaluation();
    verify(repository, never()).save(any(TaskDataChange.class));
  }
}
