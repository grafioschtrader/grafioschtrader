package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.GTSecuritiyCurrencyExchange;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetExchange;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.model.GTNetSupplierWithDetails;

/**
 * Custom repository interface for GTNetExchange operations not covered by Spring Data JPA.
 *
 * Provides methods for:
 * <ul>
 *   <li>Retrieving securities with their GTNetExchange configurations</li>
 *   <li>Retrieving currency pairs with their GTNetExchange configurations</li>
 *   <li>Batch updating multiple GTNetExchange entries</li>
 *   <li>Adding new securities/currency pairs to the exchange configuration</li>
 *   <li>Retrieving supplier details for expandable row display</li>
 * </ul>
 *
 * @see GTNetExchangeJpaRepositoryImpl for implementation details
 */
public interface GTNetExchangeJpaRepositoryCustom {

  /**
   * Retrieves all securities with their GTNetExchange configurations.
   *
   * @param activeOnly if true, only returns securities with activeToDate in the future
   * @return DTO containing securities list and their exchange configurations
   */
  GTSecuritiyCurrencyExchange<Security> getSecuritiesWithExchangeConfig(boolean activeOnly);

  /**
   * Retrieves all currency pairs with their GTNetExchange configurations.
   *
   * @return DTO containing currency pairs list and their exchange configurations
   */
  GTSecuritiyCurrencyExchange<Currencypair> getCurrencypairsWithExchangeConfigFull();

  /**
   * Batch updates multiple GTNetExchange entries.
   *
   * Only updates entries that have actually changed (dirty check).
   *
   * @param exchanges list of GTNetExchange entities to update
   * @return list of updated GTNetExchange entities
   */
  List<GTNetExchange> batchUpdate(List<GTNetExchange> exchanges);

  /**
   * Adds a new security to the GTNetExchange configuration.
   *
   * Creates a new GTNetExchange entry with all boolean fields set to false.
   *
   * @param idSecuritycurrency the ID of the security to add
   * @return the newly created GTNetExchange entry
   * @throws IllegalArgumentException if the security doesn't exist or is already configured
   */
  GTNetExchange addSecurity(Integer idSecuritycurrency);

  /**
   * Adds a new currency pair to the GTNetExchange configuration.
   *
   * Creates a new GTNetExchange entry with all boolean fields set to false.
   *
   * @param idSecuritycurrency the ID of the currency pair to add
   * @return the newly created GTNetExchange entry
   * @throws IllegalArgumentException if the currency pair doesn't exist or is already configured
   */
  GTNetExchange addCurrencypair(Integer idSecuritycurrency);

  /**
   * Retrieves supplier details for a given security or currency pair.
   *
   * Used for expandable row display to show which remote suppliers can provide
   * price data for this instrument.
   *
   * @param idSecuritycurrency the ID of the security or currency pair
   * @return DTO containing GTNetSupplier header and list of GTNetSupplierDetail entries
   */
  List<GTNetSupplierWithDetails> getSupplierDetails(Integer idSecuritycurrency);
}
