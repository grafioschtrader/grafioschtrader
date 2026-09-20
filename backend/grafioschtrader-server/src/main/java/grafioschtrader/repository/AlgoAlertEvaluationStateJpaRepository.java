package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoAlertEvaluationState;
import jakarta.persistence.LockModeType;

/** Internal scheduling state; no user-writable REST repository is exposed. */
public interface AlgoAlertEvaluationStateJpaRepository extends JpaRepository<AlgoAlertEvaluationState, Integer> {
  List<AlgoAlertEvaluationState> findByIdTenant(Integer idTenant);

  Optional<AlgoAlertEvaluationState> findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrency(Integer tenant,
      Integer strategy, Integer security);

  /** Locks the pair while claiming or committing evaluation, including after a lease expires. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM AlgoAlertEvaluationState s WHERE s.idTenant = ?1 AND s.idAlgoStrategy = ?2 AND s.idSecuritycurrency = ?3")
  Optional<AlgoAlertEvaluationState> lockPair(Integer tenant, Integer strategy, Integer security);

  /** Named query: AlgoAlertEvaluationState.ensurePair. Unique key makes concurrent initialization harmless. */
  @Modifying
  @Query(nativeQuery = true)
  void ensurePair(Integer tenant, Integer strategy, Integer security, String fingerprint);

  @Transactional
  @Modifying
  @Query("DELETE FROM AlgoAlertEvaluationState s WHERE s.idAlgoStrategy = ?1")
  void deleteByIdAlgoStrategy(Integer strategy);
}
