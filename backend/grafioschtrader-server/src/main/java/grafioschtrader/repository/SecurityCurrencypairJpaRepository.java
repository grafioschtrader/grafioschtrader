package grafioschtrader.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Securitycurrency;

@NoRepositoryBean
public interface SecurityCurrencypairJpaRepository<S extends Securitycurrency<S>> extends JpaRepository<S, Integer> {
  S findByIdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Atomically claims the right to perform an intraday price update for a single instrument by advancing
   * {@code s_timestamp} only when the currently stored value is older than the given threshold.
   *
   * <p>
   * Returns the number of affected rows (0 or 1). Without this claim two concurrent requests for the same instrument
   * both pass the in-memory staleness check and both write the {@code securitycurrency} row, which produces needless
   * row-lock contention on a hot row such as a widely used currency pair. Only the caller that receives 1 should
   * contact the data provider and save.
   * </p>
   *
   * <p>
   * The claim advances {@code s_timestamp} <em>before</em> the provider is contacted. On success the subsequent save
   * overwrites it with the timestamp delivered by the connector; on a failed fetch the save writes the previous
   * in-memory value back, so the next caller may claim again immediately. This mirrors
   * {@link WatchlistJpaRepository#updateLastTimestampIfStale(LocalDateTime, Integer, LocalDateTime)}.
   * </p>
   *
   * <p>
   * The statement is declared here rather than in {@code jpa-named-queries.properties} because it is shared by both
   * concrete repositories, whereas named-query keys are bound to a single entity name.
   * </p>
   *
   * @param idSecuritycurrency the instrument to claim
   * @param now                the new timestamp to set
   * @param threshold          only claim when the stored {@code s_timestamp} is before this value
   * @return 1 if the claim succeeded and the caller should perform the update, 0 if another caller was faster or the
   *         price is still fresh
   */
  @Transactional
  @Modifying
  @Query(value = "UPDATE securitycurrency SET s_timestamp = ?2 WHERE id_securitycurrency = ?1"
      + " AND (s_last IS NULL OR s_timestamp IS NULL OR s_timestamp < ?3)", nativeQuery = true)
  int claimIntradayUpdate(Integer idSecuritycurrency, LocalDateTime now, LocalDateTime threshold);
}
