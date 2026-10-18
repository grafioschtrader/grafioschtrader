package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.udffill.UDFFillService;
import grafioschtrader.udffill.UDFFillTaskQueue;

/**
 * Fills user-defined security fields, in one of two forms.
 * <ul>
 * <li>Without an entity it is the daily run. It recreates the persistent general fields of user 0, such as the Yahoo
 * links and the next earnings date, whose content is costly to determine because data suppliers must be contacted. It
 * then enqueues one run per user field with a template or a lookup as fill source, and one value proposal run
 * ({@code UDF_USER_SUGGEST_VALUES}) without an entity, which enqueues the language model fields with a fill period of
 * all tenants.</li>
 * <li>With the ID of a user field as entity it computes that field for every due security of the tenant scope of its
 * owner. Such a run is also enqueued when the field is saved with a changed fill configuration.</li>
 * </ul>
 * One task per user field lets a task of higher priority, such as the end-of-day price load, run between two fields.
 * Pending tasks of this type are therefore never removed wholesale; the queue skips a run that is already waiting.
 */
@Component
public class UDFFillValuesTask implements ITask {

  private final UDFFillService udfFillService;
  private final UDFFillTaskQueue udfFillTaskQueue;
  private final UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
  private final List<IUDFForEveryUser> uDFForEveryUser;

  public UDFFillValuesTask(UDFFillService udfFillService, UDFFillTaskQueue udfFillTaskQueue,
      UDFMetadataSecurityJpaRepository uMetaSecurityRepository, List<IUDFForEveryUser> uDFForEveryUser) {
    this.udfFillService = udfFillService;
    this.udfFillTaskQueue = udfFillTaskQueue;
    this.uMetaSecurityRepository = uMetaSecurityRepository;
    this.uDFForEveryUser = uDFForEveryUser;
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.UDF_FILL_VALUES;
  }

  /**
   * Enqueues the daily run with the lowest priority, unless one is already waiting.
   */
  @Scheduled(cron = "${gt.user0.persists.filds}", zone = BaseConstants.TIME_ZONE)
  public void createDailyTask() {
    udfFillTaskQueue.enqueueAll(TaskDataExecPriority.PRIO_VERY_LOW);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (taskDataChange.getIdEntity() == null) {
      uMetaSecurityRepository.recreateUDFFieldsForEveryUser(uDFForEveryUser);
      uMetaSecurityRepository
          .findUserFieldsByFillSourceIn(Set.of(UDFFillSource.TEMPLATE.getValue(), UDFFillSource.RESOLVER.getValue()))
          .forEach(f -> udfFillTaskQueue.enqueue(f.getIdUDFMetadata(), TaskDataExecPriority.PRIO_VERY_LOW));
      udfFillTaskQueue.enqueueSuggestAll(TaskDataExecPriority.PRIO_VERY_LOW);
    } else {
      udfFillService.runField(taskDataChange.getIdEntity());
    }
  }

  /** Empty for the daily run over all fields, or one user field. */
  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList("", UDFMetadataSecurity.class.getSimpleName());
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
