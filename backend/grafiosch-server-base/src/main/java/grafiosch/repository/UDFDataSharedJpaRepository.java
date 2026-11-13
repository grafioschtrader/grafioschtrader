package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.*;

import grafiosch.entities.UDFDataShared;
import grafiosch.entities.UDFDataSharedKey;

public interface UDFDataSharedJpaRepository extends JpaRepository<UDFDataShared, UDFDataSharedKey> {
  List<UDFDataShared> findById_IdUDFMetadata(Integer idUDFMetadata);

  void deleteById_IdUDFMetadata(Integer idUDFMetadata);

  @Modifying
  @Query(nativeQuery = true)
  void copyOwnerValuesToShared(Integer idUDFMetadata, Integer idUser, String entity);

  @Modifying
  @Query(nativeQuery = true)
  void putValue(Integer idUDFMetadata, Integer idEntity, String entity, String value);

  @Query(nativeQuery = true)
  List<Object[]> findValuesOfUserForEntity(Integer idUser, String entity, Integer idEntity);
}
