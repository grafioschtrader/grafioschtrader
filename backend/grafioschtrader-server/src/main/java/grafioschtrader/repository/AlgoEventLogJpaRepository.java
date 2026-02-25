package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoEventLog;

public interface AlgoEventLogJpaRepository extends JpaRepository<AlgoEventLog, Integer> {
}
