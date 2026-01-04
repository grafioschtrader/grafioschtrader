package grafioschtrader.repository;

import grafioschtrader.entities.GTNetInstrument;
import grafioschtrader.entities.Securitycurrency;

/**
 * Abstract base service for GTNet instrument pool operations.
 *
 * This class provides common functionality for managing instrument entries in the GTNet pool
 * and their associated price data. It is extended by the repository implementations for
 * security and currency pair instruments.
 *
 * @param <I> the instrument type (GTNetInstrumentSecurity or GTNetInstrumentCurrencypair)
 * @param <T> the local entity type (Security or Currencypair)
 * @see GTNetInstrumentSecurityJpaRepositoryImpl
 * @see GTNetInstrumentCurrencypairJpaRepositoryImpl
 */
public abstract class GTNetLastpriceSecurityCurrencyService<I extends GTNetInstrument, T extends Securitycurrency<T>> {

}
