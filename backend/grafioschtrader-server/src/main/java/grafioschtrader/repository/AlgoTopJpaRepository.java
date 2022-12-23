package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoTop;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

public interface AlgoTopJpaRepository extends JpaRepository<AlgoTop, Integer>, AlgoTopJpaRepositoryCustom,
    UpdateCreateDeleteWithTenantJpaRepository<AlgoTop> {

  List<AlgoTop> findByIdTenantOrderByName(Integer idTenant);

  AlgoTop findByIdTenantAndIdAlgoAssetclassSecurity(Integer idTenant, Integer idAlgoAssetclassSecurity);

  @Transactional
  @Modifying
  int deleteByIdAlgoAssetclassSecurityAndIdTenant(Integer idAlgoAssetclassSecurity, Integer idTenant);

}
