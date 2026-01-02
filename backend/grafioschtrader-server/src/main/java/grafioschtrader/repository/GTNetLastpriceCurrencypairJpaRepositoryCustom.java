package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetLastpriceCurrencypair;

public interface GTNetLastpriceCurrencypairJpaRepositoryCustom {

  /**
   * Queries currency pairs from the push-open pool by batch of fromCurrency+toCurrency tuples.
   * Uses a single SQL query with dynamic tuple IN clause for efficiency.
   *
   * @param currencyPairs list of [fromCurrency, toCurrency] pairs to query
   * @return list of matching GTNetLastpriceCurrencypair entities
   */
  List<GTNetLastpriceCurrencypair> findByCurrencyTuples(List<String[]> currencyPairs);

  /**
   * Updates the GTNetLastpriceCurrencypair push pool with prices from connector fetches.
   * For AC_PUSH_OPEN servers, this persists connector-fetched prices so they're available for remote clients.
   *
   * For each currency pair:
   * - If no entry exists in the push pool, creates a new one
   * - If an entry exists but the connector price is newer, updates it
   * - If an entry exists with a newer or equal timestamp, skips it
   *
   * @param currencypairs list of currency pairs with updated prices from connectors
   * @param idGtNet the local GTNet server ID to associate with the entries
   * @return number of entries inserted or updated
   */
  int updateFromConnectorFetch(List<Currencypair> currencypairs, Integer idGtNet);
}
