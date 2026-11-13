package grafiosch.repository;

import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.entities.User;

public interface UDFMetadataSubscriptionJpaRepositoryCustom extends BaseRepositoryCustom<UDFMetadataSubscription> {
  int delEntityWithUserId(Integer id, Integer idUser);

  void claimOwnership(Integer id, User user);
}
