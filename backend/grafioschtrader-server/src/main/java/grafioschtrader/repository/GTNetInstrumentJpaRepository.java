package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetInstrument;

/**
 * Repository for the GTNetInstrument base entity.
 *
 * This repository handles the instrument pool which identifies securities and currency pairs
 * that can be shared via GTNet. Use the specific subtype repositories
 * ({@link GTNetInstrumentSecurityJpaRepository}, {@link GTNetInstrumentCurrencypairJpaRepository})
 * for type-specific queries.
 *
 * @see GTNetInstrumentSecurityJpaRepository for security instruments
 * @see GTNetInstrumentCurrencypairJpaRepository for currency pair instruments
 */
public interface GTNetInstrumentJpaRepository extends JpaRepository<GTNetInstrument, Integer> {

  /**
   * Finds an instrument by its local securitycurrency reference.
   *
   * @param idSecuritycurrency the local securitycurrency ID
   * @return the instrument if found
   */
  Optional<GTNetInstrument> findByIdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Finds an instrument by GTNet server ID and local securitycurrency reference.
   *
   * @param idGtNet the GTNet server ID
   * @param idSecuritycurrency the local securitycurrency ID
   * @return the instrument if found
   */
  Optional<GTNetInstrument> findByIdGtNetAndIdSecuritycurrency(Integer idGtNet, Integer idSecuritycurrency);

}
