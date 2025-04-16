package grafioschtrader.task.exec;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Some global user-defined fields have a longer validity period. This can make
 * the content persistent. In addition, the effort required to create their
 * content is sometimes time-consuming, e.g. because data suppliers have to be
 * contacted. It therefore makes sense to update them daily.
 */
@Service
public class UDFUser0FillPersitentValueTask implements ITask {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private UDFMetadataSecurityJpaRepository uMetaSecurityRepository;

  @Autowired
  private List<IUDFForEveryUser> uDFForEveryUser;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.UDF_USER_0_FILL_PERSISTENT_FIELDS_WITH_VALUES;
  }

  @Scheduled(cron = "${gt.user0.persists.filds}", zone = BaseConstants.TIME_ZONE)
  public void createUDFUser0FillPersitentValueTask() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public boolean removeAllOtherJobsOfSameTask() {
    return true;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    uMetaSecurityRepository.recreateUDFFieldsForEveryUser(uDFForEveryUser);
  }

}
