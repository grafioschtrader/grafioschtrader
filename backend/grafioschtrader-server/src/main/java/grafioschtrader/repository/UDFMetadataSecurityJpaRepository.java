package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFMetadataBase.UiOrderDescriptionCount;
import grafioschtrader.rest.UpdateCreateDeleteWithUserIdJpaRepository;

public interface UDFMetadataSecurityJpaRepository
    extends JpaRepository<UDFMetadataSecurity, Integer>, UDFMetadataSecurityJpaRepositoryCustom,
    UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataSecurity> {

  UDFMetadataSecurity getByUdfSpecialTypeAndIdUser(byte udfSpecialType, int idUser);

  List<UDFMetadataSecurity> getAllByIdUserInOrderByUiOrder(int[] idUser);

  int deleteByIdUDFMetadataAndIdUser(int idUDFMetadata, int idUser);

  @Query(nativeQuery = true)
  UiOrderDescriptionCount countUiOrderAndDescription(int[] users, int uiOrder, String description);

  
}
