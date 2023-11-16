package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface UDFMetadataSecurityJpaRepository extends JpaRepository<UDFMetadataSecurity, Integer>,
    UDFMetadataSecurityJpaRepositoryCustom, UpdateCreateJpaRepository<UDFMetadataSecurity> {

  List<UDFMetadataSecurity> getAllByIdUser(Integer idUser);
  
  @Query(nativeQuery = true)
  UiOrderDescriptionCount countUiOrderAndDescription(int uiOrder, String description);
  
  
  static interface UiOrderDescriptionCount {
    int getCountUiOrder();
    int getCountDescription();
  }
}
