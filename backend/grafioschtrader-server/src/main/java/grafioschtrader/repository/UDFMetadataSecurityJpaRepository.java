package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.repository.UDFMetadataBase.UiOrderDescriptionCount;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;
import grafioschtrader.entities.UDFMetadataSecurity;

public interface UDFMetadataSecurityJpaRepository
    extends JpaRepository<UDFMetadataSecurity, Integer>, UDFMetadataSecurityJpaRepositoryCustom,
    UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataSecurity> {

  UDFMetadataSecurity getByUdfSpecialTypeAndIdUser(byte udfSpecialType, int idUser);
  Set<UDFMetadataSecurity> getByUdfSpecialTypeInAndIdUser(Set<Byte> udfSpecialTypes, int idUser);
  
  List<UDFMetadataSecurity> getAllByIdUserInAndUiOrderLessThanOrderByUiOrder(int[] idUser, byte lessThanUiOrderNum);
  
  int deleteByIdUDFMetadataAndIdUser(int idUDFMetadata, int idUser);

  /**
   * Checks if uiOrder and description are unique. Otherwise, the entry must not be written.
   */
  @Query(nativeQuery = true)
  UiOrderDescriptionCount countUiOrderAndDescription(int[] users, int uiOrder, String description);

  @Query(nativeQuery = true)
  List<UDFMetadataSecurity> getAllByIdUserInOrderByUiOrderExcludeDisabled(int idUser);
}
