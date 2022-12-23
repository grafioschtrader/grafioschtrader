package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

public interface CorrelationSetJpaRepository extends JpaRepository<CorrelationSet, Integer>,
    CorrelationSetJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<CorrelationSet> {

  List<CorrelationSet> findByIdTenantOrderByName(Integer idTenant);

  Optional<CorrelationSet> findByIdTenantAndIdCorrelationSet(Integer idTenant, Integer idCorrelationSet);

  Long countByIdTenant(Integer idTenant);

  @Query(value = "SELECT count(s) FROM CorrelationSet c JOIN c.securitycurrencyList s WHERE c.idTenant = ?1 AND c.idCorrelationSet= ?2")
  Long countInstrumentsInCorrelationSet(Integer idTenant, Integer idCorrelationSet);

  int deleteByIdCorrelationSetAndIdTenant(Integer idCorrelationSet, Integer idTenant);
}
