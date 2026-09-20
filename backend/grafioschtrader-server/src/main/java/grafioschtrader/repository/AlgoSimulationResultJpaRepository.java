package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoSimulationResult;

/**
 * The historical replay of a simulation environment. There is at most one row per environment, so the tenant is the
 * functional key; a repeat run replaces it rather than adding a second one. The rows are written by the replay service
 * alone - there is no generic create or update endpoint - and read by the run status and result view.
 */
public interface AlgoSimulationResultJpaRepository extends JpaRepository<AlgoSimulationResult, Integer> {

  Optional<AlgoSimulationResult> findByIdTenant(Integer idTenant);

  void deleteByIdTenant(Integer idTenant);
}
