package grafiosch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.*;

import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;

public interface UDFMetadataSubscriptionJpaRepository extends JpaRepository<UDFMetadataSubscription, Integer>,
    UDFMetadataSubscriptionJpaRepositoryCustom, UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataSubscription> {
  List<UDFMetadataSubscription> findByIdUser(Integer idUser);

  List<UDFMetadataSubscription> findByIdUDFMetadataOrderByIdUDFMetadataSubscription(Integer idUDFMetadata);

  Optional<UDFMetadataSubscription> findByIdUDFMetadataAndIdUser(Integer idUDFMetadata, Integer idUser);

  int countByIdUDFMetadata(Integer idUDFMetadata);

  int deleteByIdUDFMetadataSubscriptionAndIdUser(Integer id, Integer idUser);

  @Query("SELECT m.idUDFMetadata, count(s) FROM UDFMetadata m JOIN UDFMetadataSubscription s ON s.idUDFMetadata = m.idUDFMetadata WHERE m.idUser = ?1 GROUP BY m.idUDFMetadata")
  List<Object[]> countSubscribersOfOwner(Integer idUser);

  @Query(nativeQuery = true)
  List<Integer> getParticipantTenantIds(Integer idUDFMetadata);
}
