package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoAlertState;

/**
 * Access to the crossing baselines of the alert evaluation. The rows are derived state, so nothing here is exposed over
 * REST; they are read and written only by the evaluation itself.
 */
public interface AlgoAlertStateJpaRepository extends JpaRepository<AlgoAlertState, Integer> {

  /**
   * Loads the baseline of one bound. The four arguments are exactly the unique key {@code UK_AlertState}, so at most
   * one row can match.
   *
   * @param idTenant           owning tenant
   * @param idAlgoStrategy     strategy the alert belongs to
   * @param idSecuritycurrency instrument the alert was evaluated against
   * @param boundKey           which bound of the alert, for example LOWER, UPPER, MA, RSI_LOWER
   * @return the stored baseline, or empty when this bound has never been evaluated
   */
  Optional<AlgoAlertState> findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyAndBoundKey(Integer idTenant,
      Integer idAlgoStrategy, Integer idSecuritycurrency, String boundKey);

  /**
   * Discards every baseline of one strategy. Used when a strategy is deactivated, so that reactivating it later cannot
   * report a crossing that happened while the alert was off. Deleting a strategy needs no call: the foreign key
   * cascades.
   *
   * @param idAlgoStrategy strategy whose baselines are dropped
   * @return number of removed rows
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM AlgoAlertState s WHERE s.idAlgoStrategy = ?1")
  int deleteByIdAlgoStrategy(Integer idAlgoStrategy);

  /**
   * Discards the baselines of one alert on one instrument. Used for a pair whose scope is switched off, which is
   * narrower than the whole strategy because the same strategy can reach one instrument through an active path and
   * another through a deactivated one.
   *
   * @param idAlgoStrategy     the alert
   * @param idSecuritycurrency the instrument whose baselines are dropped
   * @return number of removed rows
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM AlgoAlertState s WHERE s.idAlgoStrategy = ?1 AND s.idSecuritycurrency = ?2")
  int deleteByIdAlgoStrategyAndIdSecuritycurrency(Integer idAlgoStrategy, Integer idSecuritycurrency);
}
