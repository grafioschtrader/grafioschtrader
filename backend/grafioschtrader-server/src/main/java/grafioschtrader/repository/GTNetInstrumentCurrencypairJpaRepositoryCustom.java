package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;

/**
 * Custom repository interface for GTNetInstrumentCurrencypair batch operations.
 *
 * Provides methods for efficient batch queries and updates that cannot be expressed
 * with standard Spring Data JPA derived queries.
 */
public interface GTNetInstrumentCurrencypairJpaRepositoryCustom {

  /**
   * Queries currency pair instruments from the pool by batch of fromCurrency+toCurrency tuples.
   * Uses a single SQL query with dynamic tuple IN clause for efficiency.
   *
   * @param currencyPairs list of [fromCurrency, toCurrency] pairs to query
   * @return list of matching GTNetInstrumentCurrencypair entities
   */
  List<GTNetInstrumentCurrencypair> findByCurrencyTuples(List<String[]> currencyPairs);

  /**
   * Updates the GTNet instrument pool and lastprice data from connector fetches.
   * For AC_PUSH_OPEN servers, this persists connector-fetched prices so they're available for remote clients.
   *
   * For each currency pair:
   * - If no instrument entry exists in the pool, creates one (with idSecuritycurrency set)
   * - If no lastprice entry exists, creates one linked to the instrument
   * - If a lastprice entry exists but the connector price is newer, updates it
   * - If a lastprice entry exists with a newer or equal timestamp, skips it
   *
   * @param currencypairs list of currency pairs with updated prices from connectors
   * @return number of lastprice entries inserted or updated
   */
  int updateFromConnectorFetch(List<Currencypair> currencypairs);

  /**
   * Finds or creates an instrument entry for the given currency pair.
   * The instrument is identified by from and to currency codes.
   * Locality (whether a matching local currency pair exists) is determined dynamically via JOIN query.
   *
   * @param fromCurrency the source currency code
   * @param toCurrency the target currency code
   * @return the existing or newly created instrument
   */
  GTNetInstrumentCurrencypair findOrCreateInstrument(String fromCurrency, String toCurrency);
}
