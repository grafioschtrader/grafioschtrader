package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
   * Finds all local currency pair instruments (those that exist in the local database).
   * Uses a JOIN to determine locality dynamically based on matching from/to currencies.
   *
   * @return list of local currency pair instruments
   */
  @Query(nativeQuery = true, value = """
      SELECT g.* FROM gt_net_instrument_currencypair g
      JOIN currencypair c ON g.from_currency = c.from_currency AND g.to_currency = c.to_currency""")
  List<GTNetInstrumentCurrencypair> findLocalInstruments();

  /**
   * Finds all foreign currency pair instruments (those that do NOT exist in the local database).
   * Uses a LEFT JOIN to find instruments with no matching local currency pair.
   *
   * @return list of foreign currency pair instruments
   */
  @Query(nativeQuery = true, value = """
      SELECT g.* FROM gt_net_instrument_currencypair g
      LEFT JOIN currencypair c ON g.from_currency = c.from_currency AND g.to_currency = c.to_currency
      WHERE c.id_securitycurrency IS NULL""")
  List<GTNetInstrumentCurrencypair> findForeignInstruments();

  /**
   * Finds currency pair instruments by a list of composite keys (fromCurrency|toCurrency format).
   * Used for batch lookups when matching instruments from GTNet requests.
   *
   * @param keys list of composite keys in "fromCurrency|toCurrency" format
   * @return list of matching currency pair instruments
   */
  @Query("SELECT c FROM GTNetInstrumentCurrencypair c WHERE CONCAT(c.fromCurrency, '|', c.toCurrency) IN :keys")
  List<GTNetInstrumentCurrencypair> findByCurrencyPairKeys(@Param("keys") List<String> keys);

  /**
   * Determines which GTNet currency pair instruments have matching local currency pairs via JOIN.
   * Returns mapping from GTNet instrument ID to local currencypair ID.
   *
   * Uses a native JOIN query to find local currency pairs that match the from/to currencies
   * of the given GTNet instruments. This allows dynamic determination of instrument locality
   * without storing the reference in the GTNetInstrument entity.
   *
   * @param instrumentIds list of GTNet instrument IDs to check for locality
   * @return list of Object[] where [0] = idGtNetInstrument (Integer), [1] = idSecuritycurrency (Integer)
   */
  @Query(nativeQuery = true, value = """
      SELECT g.id_gt_net_instrument, c.id_securitycurrency
      FROM gt_net_instrument_currencypair g
      JOIN currencypair c ON g.from_currency = c.from_currency AND g.to_currency = c.to_currency
      WHERE g.id_gt_net_instrument IN (:ids)""")
  List<Object[]> findLocalCurrencypairMappings(@Param("ids") List<Integer> instrumentIds);

}
