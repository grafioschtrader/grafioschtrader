package grafioschtrader.repository;

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

}
