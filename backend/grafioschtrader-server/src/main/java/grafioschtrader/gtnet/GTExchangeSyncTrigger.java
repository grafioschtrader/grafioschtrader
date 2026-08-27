package grafioschtrader.gtnet;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.TaskDataChange;
import grafiosch.gtnet.IExchangeSyncTrigger;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import grafioschtrader.task.exec.GTNetExchangeSyncTask;

/**
 * Enqueues an incremental {@link GTNetExchangeSyncTask} once a data exchange with a peer has been agreed.
 *
 * <p>
 * Both ends of the negotiation reach this: the responder when it accepts the request, the requester when the acceptance
 * comes back. Until this existed the two sides agreed on an exchange and then had no supplier-detail rows for it until
 * the daily cron ran.
 * </p>
 *
 * <p>
 * The work is scheduled rather than executed, because the caller is still inside the transaction that persists the
 * exchange and a synchronisation running before that commit would not see it. A sync that is already waiting is left
 * alone: {@code task_data_change} has no unique index, and two peers accepting within the same minute would otherwise
 * queue two identical full passes.
 * </p>
 */
@Component
public class GTExchangeSyncTrigger implements IExchangeSyncTrigger {

  private static final Logger log = LoggerFactory.getLogger(GTExchangeSyncTrigger.class);

  /** Long enough for the accepting transaction to have committed when the background worker picks the task up. */
  private static final int DELAY_MINUTES = 1;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public void scheduleExchangeSync(GTNet remoteGTNet) {
    if (taskDataChangeJpaRepository.existsByIdTaskAndProgressStateType(TaskTypeBase.GTNET_EXCHANGE_SYNC.getValue(),
        ProgressStateType.PROG_WAITING.getValue())) {
      log.debug("Exchange sync already queued, not enqueuing another one for {}",
          remoteGTNet == null ? "unknown peer" : remoteGTNet.getDomainRemoteName());
      return;
    }
    taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeBase.GTNET_EXCHANGE_SYNC,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(DELAY_MINUTES),
        GTNetExchangeSyncTask.INCREMENTAL_MODE, GTNetExchangeSyncTask.SYNC_MODE_ENTITY));
    log.info("Queued incremental GTNet exchange sync after agreeing data exchange with {}",
        remoteGTNet == null ? "unknown peer" : remoteGTNet.getDomainRemoteName());
  }
}
