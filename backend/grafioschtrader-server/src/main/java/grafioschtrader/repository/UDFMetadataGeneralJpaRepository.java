package grafioschtrader.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.UDFMetadataGeneral;
import grafioschtrader.repository.UDFMetadataBase.UiOrderDescriptionCount;
import grafioschtrader.rest.UpdateCreateDeleteWithUserIdJpaRepository;

public interface UDFMetadataGeneralJpaRepository extends JpaRepository<UDFMetadataGeneral, Integer>,
    UDFMetadataGeneralJpaRepositoryCustom, UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataGeneral> {

  List<UDFMetadataGeneral> getAllByIdUserInOrderByUiOrder(int[] idUser);
  
  List<UDFMetadataGeneral> getAllByIdUserInAndEntityOrderByUiOrder(int[] idUser, String entity);
  
  int deleteByIdUDFMetadataAndIdUser(int idUDFMetadata, int idUser);
  
  @Query(nativeQuery = true)
  UiOrderDescriptionCount countUiOrderAndDescription(int[] users, String entityName, int uiOrder, String description);
 
  public default List<String> getSupportedEntities() {
    return GlobalConstants.UDF_GENERAL_ENTITIES.stream().map(c -> c.getSimpleName()).collect(Collectors.toList());
  }
}
