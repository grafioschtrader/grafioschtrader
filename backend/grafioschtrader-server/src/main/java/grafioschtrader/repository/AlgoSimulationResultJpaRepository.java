package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoSimulationResult;

public interface AlgoSimulationResultJpaRepository extends JpaRepository<AlgoSimulationResult, Integer> {
}
