package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.dto.TaskDataChangeFormConstraints;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.task.ITask;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;

public class TaskDataChangeJpaRepositoryImpl extends BaseRepositoryImpl<TaskDataChange>
    implements TaskDataChangeJpaRepositoryCustom {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired(required = false)
  private List<ITask> tasks = new ArrayList<>();

  @Autowired(required = false)
  private List<EntityIdOptionsProvider> entityIdOptionsProviders;

  @Override
  public TaskDataChange saveOnlyAttributes(final TaskDataChange taskDataChange, final TaskDataChange existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    checkTaskDataChange(taskDataChange, getFormConstraints());
    taskDataChange.setCreationTime(LocalDateTime.now());
    taskDataChange.setExecutionPriority(TaskDataExecPriority.PRIO_NORMAL);
    taskDataChange.setProgressStateType(ProgressStateType.PROG_WAITING);
    return taskDataChangeJpaRepository.save(taskDataChange);
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
    addEntityIdOptions(taskDataChangeConfig);
    composeEntityIdOptions(taskDataChangeConfig);
    return taskDataChangeConfig;
  }

  /**
   * Calls all registered EntityIdOptionsProvider implementations to add entity ID options to the form constraints.
   * Providers register into the {@code sharedEntityIdOptions} / {@code taskEntityIdOptions} staging maps; the
   * task-scoped {@code entityIdOptions} map the frontend consumes is built afterwards by
   * {@link #composeEntityIdOptions(TaskDataChangeFormConstraints)}.
   *
   * @param constraints the form constraints to populate with entity ID options
   */
  private void addEntityIdOptions(TaskDataChangeFormConstraints constraints) {
    if (entityIdOptionsProviders != null) {
      entityIdOptionsProviders.forEach(provider -> provider.addEntityIdOptions(constraints));
    }
  }

  /**
   * Builds the task-scoped {@code entityIdOptions} map from the staging maps. For every task and each of its allowed
   * entities, a task-specific override wins over a shared option list. Tasks with no matching options are left out so
   * the frontend falls back to a numeric input for those entities.
   *
   * @param constraints the form constraints whose {@code entityIdOptions} is assembled
   */
  private void composeEntityIdOptions(TaskDataChangeFormConstraints constraints) {
    constraints.taskTypeConfig.forEach((taskType, allowedEntities) -> {
      Map<String, List<ValueKeyHtmlSelectOptions>> perTask = new HashMap<>();
      Map<String, List<ValueKeyHtmlSelectOptions>> taskSpecific = constraints.taskEntityIdOptions.getOrDefault(taskType,
          Map.of());
      for (String entity : allowedEntities) {
        List<ValueKeyHtmlSelectOptions> options = taskSpecific.containsKey(entity) ? taskSpecific.get(entity)
            : constraints.sharedEntityIdOptions.get(entity);
        if (options != null) {
          perTask.put(entity, options);
        }
      }
      if (!perTask.isEmpty()) {
        constraints.entityIdOptions.put(taskType, perTask);
      }
    });
  }

  private void checkTaskDataChange(final TaskDataChange taskDataChange, final TaskDataChangeFormConstraints tdcfc) {
    if (taskDataChange.getIdTask().getValue() > tdcfc.maxUserCreateTask
        && taskDataChange.getEarliestStartTime().minusDays(tdcfc.maxDaysInFuture).isAfter(LocalDateTime.now())) {
      throw new GeneralNotTranslatedWithArgumentsException("g.taskdatachange.user.definition", null);
    }

  }

}
