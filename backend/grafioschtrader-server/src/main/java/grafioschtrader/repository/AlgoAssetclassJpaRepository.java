package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

@Repository
public interface AlgoAssetclassJpaRepository extends JpaRepository<AlgoAssetclass, Integer>,
    AlgoAssetclassJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<AlgoAssetclass> {

  List<AlgoAssetclass> findByIdTenantAndIdAlgoAssetclassParent(Integer idTenant, Integer idAlgoAssetclassParent);

  @Transactional
  @Modifying
  int deleteByIdAlgoAssetclassSecurityAndIdTenant(Integer idAlgoAssetclassSecurity, Integer idTenant);
}
