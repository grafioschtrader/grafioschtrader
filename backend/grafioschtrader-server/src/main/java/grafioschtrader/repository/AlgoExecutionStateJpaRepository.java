package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoExecutionState;

public interface AlgoExecutionStateJpaRepository extends JpaRepository<AlgoExecutionState, Integer> {
}
