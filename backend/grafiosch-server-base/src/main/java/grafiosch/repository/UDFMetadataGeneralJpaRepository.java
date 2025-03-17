package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.repository.UDFMetadataBase.UiOrderDescriptionCount;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;

public interface UDFMetadataGeneralJpaRepository extends JpaRepository<UDFMetadataGeneral, Integer>,
    UDFMetadataGeneralJpaRepositoryCustom, UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataGeneral> {

  List<UDFMetadataGeneral> getAllByIdUserInOrderByUiOrder(int[] idUser);
  
  List<UDFMetadataGeneral> getAllByIdUserInAndEntityOrderByUiOrder(int[] idUser, String entity);
  
  int deleteByIdUDFMetadataAndIdUser(int idUDFMetadata, int idUser);
  
  @Query(nativeQuery = true)
  UiOrderDescriptionCount countUiOrderAndDescription(int[] users, String entityName, int uiOrder, String description);
 
//  public default List<String> getSupportedEntities() {
//    return UDFDataJpaRepositoryImpl.UDF_GENERAL_ENTITIES.stream().map(c -> c.getSimpleName()).collect(Collectors.toList());
//  }
}
