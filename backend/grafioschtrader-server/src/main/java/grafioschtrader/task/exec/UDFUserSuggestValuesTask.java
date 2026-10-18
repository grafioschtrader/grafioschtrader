package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.config.TaskQueueConfig;
import grafioschtrader.entities.Tenant;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.udffill.ai.ValueSuggestService;

/**
 * Lets a language model propose values of one user-defined security field with the fill source LLM. The entity ID of
 * the task is the ID of the field. The task is paid and always uses the language models of the home tenant of the
 * owner of the field. Two forms enqueue the runs of the fields:
 * <ul>
 * <li>Without an entity, as created daily by {@code UDF_FILL_VALUES} or by the administrator, it enqueues one run per
 * language model field with a fill period of all tenants.</li>
 * <li>With a tenant as entity, created by the administrator, it does so for the fields of the users of this
 * tenant.</li>
 * </ul>
 * The run of one field, with the field as entity, is enqueued by those two forms and when the owner asks the model for
 * values; the administrator does not create it.
 */
@Component
public class UDFUserSuggestValuesTask implements ITask {

  private final ValueSuggestService valueSuggestService;

  public UDFUserSuggestValuesTask(ValueSuggestService valueSuggestService) {
    this.valueSuggestService = valueSuggestService;
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.UDF_USER_SUGGEST_VALUES;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (taskDataChange.getIdEntity() == null || Tenant.class.getSimpleName().equals(taskDataChange.getEntity())) {
      valueSuggestService.enqueuePeriodic(taskDataChange.getIdEntity());
      return;
    }
    String failure = valueSuggestService.runField(taskDataChange.getIdEntity());
    if (failure != null) {
      throw new TaskBackgroundException(failure, false);
    }
  }

  /** Empty for all tenants, or one tenant. */
  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList("", Tenant.class.getSimpleName());
  }

  /** Runs in the queue of the language model tasks, beside the main queue and with several workers. */
  @Override
  public String getQueue() {
    return TaskQueueConfig.AI_QUEUE;
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
