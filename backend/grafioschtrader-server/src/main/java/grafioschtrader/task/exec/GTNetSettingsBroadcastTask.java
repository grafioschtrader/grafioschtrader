package grafioschtrader.task.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskTypeBase;
import grafioschtrader.service.GlobalparametersService;

/**
 * Background task that broadcasts settings changes to all configured GTNet peers.
 *
 * When the local GTNet entity settings change (maxLimit, acceptRequest, serverState, dailyRequestLimit), this task
 * sends GT_NET_SETTINGS_UPDATED_ALL_C notifications to all peers with configured exchange. Running this as a
 * background task prevents the UI from blocking during network operations.
 *
 * The task is triggered by saving changes to the local GTNet entity via the frontend. Instead of sending broadcasts
 * synchronously during the save operation, a TaskDataChange is created which schedules this task for immediate
 * execution in the background.
 */
@Component
public class GTNetSettingsBroadcastTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetSettingsBroadcastTask.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.GTNET_SETTINGS_BROADCAST;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping settings broadcast");
      return;
    }

    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.info("GTNet my entry ID not configured, skipping settings broadcast");
      return;
    }

    try {
      gtNetJpaRepository.broadcastSettingsUpdate();
      log.info("Successfully broadcasted settings update to GTNet peers");
    } catch (Exception e) {
      log.error("Failed to broadcast settings update to GTNet peers", e);
      throw new TaskBackgroundException("gtnet.settings.broadcast.failed", false);
    }
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    // Remove any other pending settings broadcast tasks to avoid duplicate notifications.
    // If multiple settings changes happen quickly, only the latest needs to be sent.
    return true;
  }
}
