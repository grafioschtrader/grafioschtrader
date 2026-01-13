package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetSupplierDetail;

public interface GTNetSupplierDetailJpaRepository extends JpaRepository<GTNetSupplierDetail, Integer> {
  
  @Query(value = "SELECT DISTINCT id_securitycurrency FROM gt_net_supplier_detail WHERE id_securitycurrency IN (?1)", nativeQuery = true)
  Set<Integer> findIdSecuritycurrencyWithDetails(List<Integer> ids);
}
