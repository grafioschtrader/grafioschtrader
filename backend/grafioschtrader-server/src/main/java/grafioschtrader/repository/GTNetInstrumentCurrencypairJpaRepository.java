package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetInstrumentCurrencypair;

/**
 * Repository for GTNetInstrumentCurrencypair entities.
 *
 * Provides access to currency pair instruments in the GTNet instrument pool, identified by from/to currency.
 * For price data operations, use this repository to find instruments and then query GTNetLastpriceJpaRepository
 * for the associated price data.
 *
 * @see GTNetInstrumentCurrencypairJpaRepositoryCustom for batch operations
 * @see GTNetLastpriceJpaRepository for price data
 */
public interface GTNetInstrumentCurrencypairJpaRepository
    extends JpaRepository<GTNetInstrumentCurrencypair, Integer>, GTNetInstrumentCurrencypairJpaRepositoryCustom {

  /**
   * Finds a currency pair instrument by its from and to currency.
   *
   * @param fromCurrency the source currency code (ISO 4217)
   * @param toCurrency the target currency code (ISO 4217)
   * @return the instrument if found
   */
  Optional<GTNetInstrumentCurrencypair> findByFromCurrencyAndToCurrency(String fromCurrency, String toCurrency);

  /**
   * Finds a currency pair instrument by GTNet server ID and currencies.
   *
   * @param idGtNet the GTNet server ID
   * @param fromCurrency the source currency code
   * @param toCurrency the target currency code
   * @return the instrument if found
   */
  Optional<GTNetInstrumentCurrencypair> findByIdGtNetAndFromCurrencyAndToCurrency(Integer idGtNet, String fromCurrency,
      String toCurrency);

  /**
   * Finds all currency pair instruments for a specific GTNet server.
   *
   * @param idGtNet the GTNet server ID
   * @return list of currency pair instruments
   */
  List<GTNetInstrumentCurrencypair> findByIdGtNet(Integer idGtNet);

  /**
   * Finds all local currency pair instruments (those that exist in the local database).
   *
   * @param idGtNet the GTNet server ID
   * @return list of local currency pair instruments
   */
  @Query("SELECT c FROM GTNetInstrumentCurrencypair c WHERE c.idGtNet = ?1 AND c.idSecuritycurrency IS NOT NULL")
  List<GTNetInstrumentCurrencypair> findLocalInstrumentsByIdGtNet(Integer idGtNet);

  /**
   * Finds all foreign currency pair instruments (those that do NOT exist in the local database).
   *
   * @param idGtNet the GTNet server ID
   * @return list of foreign currency pair instruments
   */
  @Query("SELECT c FROM GTNetInstrumentCurrencypair c WHERE c.idGtNet = ?1 AND c.idSecuritycurrency IS NULL")
  List<GTNetInstrumentCurrencypair> findForeignInstrumentsByIdGtNet(Integer idGtNet);

}
