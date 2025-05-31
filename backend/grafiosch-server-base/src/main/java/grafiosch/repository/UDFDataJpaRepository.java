package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.entities.UDFMetadata;

public interface UDFDataJpaRepository extends JpaRepository<UDFData, UDFDataKey>, UDFDataJpaRepositoryCustom {

  public static interface IUDFRepository<S extends UDFMetadata> {
    List<S> getMetadataByUserAndEntityAndIdEntity(Integer idUser, String entity, Integer idEntity);

    List<String> getSupportedEntities();
  }

}
