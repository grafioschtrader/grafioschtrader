package grafioschtrader.repository;

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

}
