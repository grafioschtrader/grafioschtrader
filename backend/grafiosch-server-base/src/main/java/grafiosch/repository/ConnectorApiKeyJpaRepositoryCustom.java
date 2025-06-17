package grafiosch.repository;

import java.util.Map;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepositoryImpl.SubscriptionTypeReadableName;

/**
 * Custom repository interface for connector API key operations.
 * 
 * <p>This interface extends the standard JPA repository functionality for connector API keys
 * with application-specific operations. Connector API keys are used to store encrypted
 * API credentials for external service providers, enabling the application to integrate
 * with various third-party APIs and services.</p>
 * 
 */
public interface ConnectorApiKeyJpaRepositoryCustom {

  ConnectorApiKey saveOnlyAttributes(ConnectorApiKey connectorApiKey);

  /**
   * Retrieves subscription type information organized by provider.
   * 
   * <p>This method returns a comprehensive mapping of provider identifiers to their
   * available subscription types and readable display names. The information is used
   * to populate user interface elements and provide context about available
   * subscription tiers for each external service provider.</p>
   * 
   * @return map of provider IDs to subscription type information, never null
   */
  Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType();

  void deleteConnectorApiKeyByIdProvider(String idProvider);
}
