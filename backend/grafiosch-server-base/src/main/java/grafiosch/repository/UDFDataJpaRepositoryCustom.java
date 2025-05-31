package grafiosch.repository;

import java.util.Optional;

import grafiosch.entities.UDFData;

public interface UDFDataJpaRepositoryCustom {
  UDFData createUpdate(UDFData udfData) throws Exception;

  Optional<UDFData> getUDFDataByIdUserAndEntityAndIdEntity(Integer idUser, String entity, Integer idEntity);
}
