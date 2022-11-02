package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.dto.TaskDataChangeFormConstraints;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.task.ITask;
import grafioschtrader.types.ProgressStateType;
import grafioschtrader.types.TaskDataExecPriority;

public class TaskDataChangeJpaRepositoryImpl extends BaseRepositoryImpl<TaskDataChange>
    implements TaskDataChangeJpaRepositoryCustom {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired(required = false)
  private List<ITask> tasks = new ArrayList<>();

  @Override
  public TaskDataChange saveOnlyAttributes(final TaskDataChange taskDataChange, final TaskDataChange existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    checkTaskDataChange(taskDataChange, getFormConstraints());
    taskDataChange.setCreationTime(LocalDateTime.now());
    taskDataChange.setExecutionPriority(TaskDataExecPriority.PRIO_NORMAL);
    taskDataChange.setProgressStateType(ProgressStateType.PROG_WAITING);
    return taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public TaskDataChangeFormConstraints getFormConstraints() {
    var taskDataChangeConfig = new TaskDataChangeFormConstraints();
    tasks.stream()
        .filter(
            t -> t.getTaskType().getValue() <= taskDataChangeConfig.maxUserCreateTask && t.getAllowedEntities() != null)
        .forEach(t -> taskDataChangeConfig.taskTypeConfig.put(t.getTaskType(), t.getAllowedEntities()));
    taskDataChangeConfig.canBeInterruptedList = tasks.stream()
        .filter(t -> t.getTaskType().getValue() <= taskDataChangeConfig.maxUserCreateTask && t.canBeInterrupted())
        .map(t -> t.getTaskType()).collect(Collectors.toList());
    return taskDataChangeConfig;
  }

  private void checkTaskDataChange(final TaskDataChange taskDataChange, final TaskDataChangeFormConstraints tdcfc) {
    if (taskDataChange.getIdTask().getValue() > tdcfc.maxUserCreateTask
        && taskDataChange.getEarliestStartTime().minusDays(tdcfc.maxDaysInFuture).isAfter(LocalDateTime.now())) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.taskdatachange.user.definition", null);
    }

  }

}
