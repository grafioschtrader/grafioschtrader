package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetConfig;

/**
 * Repository for managing GTNetConfig entities.
 *
 * GTNetConfig stores the local configuration for remote GTNet connections, including authentication tokens exchanged
 * during the handshake process. Each GTNet entry has an optional 1:1 relationship with a GTNetConfig, which is created
 * after a successful handshake.
 */
public interface GTNetConfigJpaRepository extends JpaRepository<GTNetConfig, Integer> {

  /**
   * Finds all GTNetConfig entries where the handshake completed after the specified timestamp.
   * Used by GTNetFutureMessageDeliveryTask to find new partners who should receive pending messages.
   *
   * @param timestamp the timestamp after which handshakes should have occurred
   * @return list of configs with handshake after the given timestamp
   */
  List<GTNetConfig> findByHandshakeTimestampAfter(LocalDateTime timestamp);

}
