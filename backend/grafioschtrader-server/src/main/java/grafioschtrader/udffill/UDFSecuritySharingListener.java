package grafioschtrader.udffill;

import org.springframework.stereotype.Component;

import grafiosch.entities.UDFMetadata;
import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.repository.IUDFSharingListener;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.types.UDFFillSource;

/**
 * Recomputes the automatic scope of a field with a template or a lookup when a participant joins, leaves or takes
 * ownership. A language model field gets no run from here: its runs are paid and start only on request of a
 * participant or with the fill period.
 */
@Component
public class UDFSecuritySharingListener implements IUDFSharingListener {
  private final UDFFillTaskQueue queue;

  public UDFSecuritySharingListener(UDFFillTaskQueue queue) {
    this.queue = queue;
  }

  @Override
  public void afterSubscribed(UDFMetadata field, UDFMetadataSubscription subscription) {
    enqueue(field);
  }

  @Override
  public void afterUnsubscribed(UDFMetadata field, Integer idUser) {
    enqueue(field);
  }

  @Override
  public void afterOwnerClaimed(UDFMetadata field) {
    enqueue(field);
  }

  private void enqueue(UDFMetadata field) {
    if (field instanceof UDFMetadataSecurity security
        && (security.getFillSource() == UDFFillSource.TEMPLATE || security.getFillSource() == UDFFillSource.RESOLVER)) {
      queue.enqueue(field.getId(), TaskDataExecPriority.PRIO_NORMAL);
    }
  }
}
