package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.config.TaskQueueConfig;
import grafioschtrader.entities.Tenant;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.udffill.ai.ValueSuggestService;

/**
 * Lets a language model propose values of one user-defined security field with the fill source LLM. The task is paid.
 * A run has one payer and asks only for the securities of the tenant scope of this payer, with the language models of
 * the home tenant of this payer:
 * <ul>
 * <li>With a field as entity the payer is the owner of the field.</li>
 * <li>With an adoption ({@code UDFMetadataSubscription}) as entity the payer is the user who adopted the shared
 * field.</li>
 * </ul>
 * Two forms enqueue these runs:
 * <ul>
 * <li>Without an entity, as created daily by {@code UDF_FILL_VALUES} or by the administrator, it enqueues the runs of
 * the owner and of every user who adopted it per language model field with a fill period of all tenants.</li>
 * <li>With a tenant as entity, created by the administrator, it does so for the users of this tenant.</li>
 * </ul>
 * The run of one payer is enqueued by those two forms and when this participant asks the model for values; the
 * administrator does not create it.
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
    String failure = UDFMetadataSubscription.class.getSimpleName().equals(taskDataChange.getEntity())
        ? valueSuggestService.runSubscription(taskDataChange.getIdEntity())
        : valueSuggestService.runField(taskDataChange.getIdEntity());
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
