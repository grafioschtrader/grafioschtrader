package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.StandingOrder;

public interface StandingOrderJpaRepository extends JpaRepository<StandingOrder, Integer>,
    StandingOrderJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<StandingOrder> {

  List<StandingOrder> findByIdTenant(Integer idTenant);

  List<StandingOrder> findByNextExecutionDateNotNullAndNextExecutionDateLessThanEqual(LocalDate date);

  long countByIdTenant(Integer idTenant);

  @Transactional
  @Modifying
  int deleteByIdStandingOrderAndIdTenant(Integer idStandingOrder, Integer idTenant);
}
