package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetSupplierDetail;

public interface GTNetSupplierDetailJpaRepository extends JpaRepository<GTNetSupplierDetail, Integer> {
  
  @Query("SELECT DISTINCT d.securitycurrency.idSecuritycurrency FROM GTNetSupplierDetail d WHERE d.securitycurrency.idSecuritycurrency IN ?1")
  Set<Integer> findIdSecuritycurrencyWithDetails(List<Integer> ids);
}
