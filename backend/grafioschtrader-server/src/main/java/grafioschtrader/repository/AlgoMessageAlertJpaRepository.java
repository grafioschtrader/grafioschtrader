package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoMessageAlert;

public interface AlgoMessageAlertJpaRepository extends JpaRepository<AlgoMessageAlert, Integer> {

  List<AlgoMessageAlert> findByIdTenantAndIdAlgoStrategy(Integer idTenant, Integer idAlgoStrategy);
}
