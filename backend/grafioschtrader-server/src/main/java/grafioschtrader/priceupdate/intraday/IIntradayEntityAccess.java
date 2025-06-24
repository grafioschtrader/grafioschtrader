package grafioschtrader.priceupdate.intraday;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Securitycurrency;

/**
 * Interface for entity-specific intraday price update operations through feed connectors.
 * 
 * <p>This interface defines the contract for performing the actual intraday price retrieval and entity update
 * operations. It serves as an abstraction layer between the intraday update orchestration logic and the
 * entity-specific implementation details required for different types of securities and currency pairs.</p>
 * 
 * <p>Implementations of this interface are responsible for:
 * <ul>
 * <li><strong>Data Retrieval</strong>: Fetching current market data from external providers via feed connectors</li>
 * <li><strong>Entity Updates</strong>: Applying retrieved price data to the security currency entity fields</li>
 * <li><strong>Timestamp Management</strong>: Setting appropriate timestamps for the updated price information</li>
 * <li><strong>Validation</strong>: Ensuring data integrity and handling provider-specific data formats</li>
 * </ul></p>
 * 
 * <p>This interface enables different update strategies for various entity types (Security vs. Currencypair)
 * while maintaining a consistent interface for the intraday update orchestration layer. Implementations
 * should handle feed connector communication, data parsing, and entity state updates in a thread-safe manner.</p>
 * 
 * @param <S> the type of security currency extending Securitycurrency (Security or Currencypair)
 */
public interface IIntradayEntityAccess<S extends Securitycurrency<S>> {
  
  /**
   * Updates the intraday price information for a security currency entity using the specified feed connector.
   * 
   * <p>This method performs the core intraday update operation by coordinating data retrieval from external
   * providers and applying the results to the entity. The implementation should:
   * <ul>
   * <li><strong>Data Fetching</strong>: Use the feed connector to retrieve current market data from the external provider</li>
   * <li><strong>Data Processing</strong>: Parse and validate the received data according to provider-specific formats</li>
   * <li><strong>Entity Updates</strong>: Apply the new price information to appropriate entity fields (sLast, sTimestamp, etc.)</li>
   * <li><strong>Error Handling</strong>: Properly handle data provider errors, network issues, and malformed responses</li>
   * </ul></p>
   * 
   * <p>The method should update the entity's state directly without persisting changes to the database,
   * as persistence is typically handled by the calling intraday update orchestration layer. This separation
   * of concerns allows for better transaction management and retry logic at the orchestration level.</p>
   * 
   * <p><strong>Thread Safety:</strong> Implementations should be thread-safe as this method may be called
   * concurrently for different entities during batch intraday update operations.</p>
   * 
   * @param securitycurrency the security or currency pair entity to update with new intraday price data
   * @param feedConnector the feed connector configured for this entity's data source and capable of retrieving intraday data
   * @throws Exception if the update operation fails due to network issues, data provider errors, 
   *                   invalid responses, or other technical problems during the update process
   */
  void updateIntraSecurityCurrency(final S securitycurrency, final IFeedConnector feedConnector) throws Exception;
}
