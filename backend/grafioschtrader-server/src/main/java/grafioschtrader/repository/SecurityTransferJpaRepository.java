package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.SecurityTransfer;

public interface SecurityTransferJpaRepository extends JpaRepository<SecurityTransfer, Integer> {

  List<SecurityTransfer> findByIdTenantOrderByTransferDateDesc(Integer idTenant);
}
