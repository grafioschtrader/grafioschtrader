package grafiosch.repository;

import org.springframework.data.jpa.repository.*;

import grafiosch.entities.UDFMetadata;

public interface UDFMetadataJpaRepository extends JpaRepository<UDFMetadata, Integer> {
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE udf_metadata SET id_user = NULL WHERE id_udf_metadata = ?1 AND id_user = ?2", nativeQuery = true)
  int orphan(Integer idUDFMetadata, Integer idUser);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE udf_metadata SET id_user = ?2, ui_order = ?3 WHERE id_udf_metadata = ?1 AND id_user IS NULL", nativeQuery = true)
  int claim(Integer idUDFMetadata, Integer idUser, byte uiOrder);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(nativeQuery = true)
  int deleteOrphanWithoutSubscription(Integer idUDFMetadata);
}
