package grafiosch.repository;

import java.util.Map;

import grafiosch.dto.SubscriptionTypeReadableName;
import grafiosch.entities.ConnectorApiKey;

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
   * Retrieves subscription type mappings for all API key-based feed connectors.
   * Maps connector short IDs to their readable names and associated subscription types.
   * Subscription types are matched by name prefix matching the connector's short ID.
   * 
   * @return map of connector short IDs to subscription type information
   */
  Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType();

  void deleteConnectorApiKeyByIdProvider(String idProvider);
}
