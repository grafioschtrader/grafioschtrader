package grafiosch.task.exec;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.MailSendRecvJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * Scheduled task that physically deletes role-addressed messages once every corresponding role member has marked them as
 * deleted.
 *
 * <p>Messages addressed to a role are never deleted directly by a user; instead a {@code mail_send_recv_read_del} row
 * with {@code mark_hide_del = 1} is created (soft delete). This task removes a whole role conversation thread as soon as
 * all corresponding members (the role's members at the message's send time) have soft-deleted the role message. The
 * sender's personal 'S' copy is left untouched and associated read/delete rows are removed via the database CASCADE
 * constraint.
 *
 * <p>Runs daily at a configurable time (default 23:30 UTC, see {@code g.purge.mail.role.cron}).
 */
@Component
public class MailRoleMessagePurgeTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(MailRoleMessagePurgeTask.class);

  @Autowired
  private MailSendRecvJpaRepository mailSendRecvJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  /**
   * Scheduled method that creates the purge task. Runs at the configured cron expression (default: 23:30 daily).
   */
  @Scheduled(cron = "${g.purge.mail.role.cron:0 30 23 * * ?}", zone = BaseConstants.TIME_ZONE)
  public void purgeFullyDeletedRoleMessages() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.MAIL_ROLE_MESSAGE_PURGE;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    List<Integer> keys = mailSendRecvJpaRepository.findPurgeableRoleThreadKeys();
    if (!keys.isEmpty()) {
      int rows = mailSendRecvJpaRepository.deleteThreadsByIdReplyToLocalIn(keys);
      log.info("Purged {} role mail rows in {} fully-deleted threads", rows, keys.size());
    }
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
