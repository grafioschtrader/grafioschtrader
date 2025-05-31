package grafiosch.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * A verification token is created during user registration. In a further step, this must be confirmed by the future
 * user by e-mail. If this verification is not successful, the user and the verification token will be deleted.
 */
@Component
public class TokensPurgeTask implements ITask {

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Scheduled(cron = "${gt.purge.cron.expression}", zone = BaseConstants.TIME_ZONE)
  public void purgeExpired() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.TOKEN_USER_REGISTRATION_PURGE;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    userJpaRepository.removeWithExpiredVerificationToken();
  }
}