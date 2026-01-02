package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.entities.Security;

public interface GTNetLastpriceSecurityJpaRepositoryCustom {

  /**
   * Queries securities from the push-open pool by batch of ISIN+currency tuples.
   * Uses a single SQL query with dynamic tuple IN clause for efficiency.
   *
   * @param isinCurrencyPairs list of [isin, currency] pairs to query
   * @return list of matching GTNetLastpriceSecurity entities
   */
  List<GTNetLastpriceSecurity> findByIsinCurrencyTuples(List<String[]> isinCurrencyPairs);

  /**
   * Updates the GTNetLastpriceSecurity push pool with prices from connector fetches.
   * For AC_PUSH_OPEN servers, this persists connector-fetched prices so they're available for remote clients.
   *
   * For each security:
   * - If no entry exists in the push pool, creates a new one
   * - If an entry exists but the connector price is newer, updates it
   * - If an entry exists with a newer or equal timestamp, skips it
   *
   * @param securities list of securities with updated prices from connectors
   * @param idGtNet the local GTNet server ID to associate with the entries
   * @return number of entries inserted or updated
   */
  int updateFromConnectorFetch(List<Security> securities, Integer idGtNet);
}
