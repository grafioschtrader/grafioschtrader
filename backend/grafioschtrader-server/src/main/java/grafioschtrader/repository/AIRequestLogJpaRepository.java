package grafioschtrader.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.AIRequestLog;

/**
 * Repository of the log of the round trips to a language model. Written only by {@code AIRequestGate}.
 */
public interface AIRequestLogJpaRepository extends JpaRepository<AIRequestLog, Integer> {

  /**
   * Counts the reservations of the whole instance since a point in time, whatever their status.
   *
   * @param from the start of the current UTC day
   * @return the number of reservations
   */
  long countByKeySourceAndRequestTimeGreaterThanEqual(byte keySource, LocalDateTime from);

  /**
   * Counts the requests over one tenant key since the given UTC time, for the daily limit of the tenant.
   *
   * @param idTenantAIProvider the tenant key
   * @param from               the start of the current UTC day
   * @return the number of reserved requests, whatever their outcome
   */
  long countByIdTenantAIProviderAndRequestTimeGreaterThanEqual(Integer idTenantAIProvider, LocalDateTime from);

  /**
   * Deletes the rows older than the retention; called by the gate before it counts.
   *
   * @param before rows reserved before this UTC time are deleted
   * @return the number of deleted rows
   */
  @Modifying
  @Query(value = "DELETE FROM ai_request_log WHERE request_time < ?1", nativeQuery = true)
  int deleteOlderThan(LocalDateTime before);
}
