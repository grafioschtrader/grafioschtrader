package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.Security;

/**
 * Custom repository interface for GTNetInstrumentSecurity batch operations.
 *
 * Provides methods for efficient batch queries and updates that cannot be expressed
 * with standard Spring Data JPA derived queries.
 */
public interface GTNetInstrumentSecurityJpaRepositoryCustom {

  /**
   * Queries security instruments from the pool by batch of ISIN+currency tuples.
   * Uses a single SQL query with dynamic tuple IN clause for efficiency.
   *
   * @param isinCurrencyPairs list of [isin, currency] pairs to query
   * @return list of matching GTNetInstrumentSecurity entities
   */
  List<GTNetInstrumentSecurity> findByIsinCurrencyTuples(List<String[]> isinCurrencyPairs);

  /**
   * Updates the GTNet instrument pool and lastprice data from connector fetches.
   * For AC_PUSH_OPEN servers, this persists connector-fetched prices so they're available for remote clients.
   *
   * For each security:
   * - If no instrument entry exists in the pool, creates one (with idSecuritycurrency set)
   * - If no lastprice entry exists, creates one linked to the instrument
   * - If a lastprice entry exists but the connector price is newer, updates it
   * - If a lastprice entry exists with a newer or equal timestamp, skips it
   *
   * @param securities list of securities with updated prices from connectors
   * @return number of lastprice entries inserted or updated
   */
  int updateFromConnectorFetch(List<Security> securities);

  /**
   * Finds or creates an instrument entry for the given security.
   * The instrument is identified by ISIN and currency.
   * Locality (whether a matching local security exists) is determined dynamically via JOIN query.
   *
   * @param isin the ISIN code
   * @param currency the currency code
   * @return the existing or newly created instrument
   */
  GTNetInstrumentSecurity findOrCreateInstrument(String isin, String currency);
}
