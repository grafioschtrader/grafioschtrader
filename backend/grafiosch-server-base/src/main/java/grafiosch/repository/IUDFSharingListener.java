package grafiosch.repository;

import grafiosch.entities.UDFMetadata;
import grafiosch.entities.UDFMetadataSubscription;

/** Application callbacks, invoked in the transaction that changes participation in a shared field. */
public interface IUDFSharingListener {
  void afterSubscribed(UDFMetadata field, UDFMetadataSubscription subscription);

  void afterUnsubscribed(UDFMetadata field, Integer idUser);

  void afterOwnerClaimed(UDFMetadata field);
}
