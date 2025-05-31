package grafiosch.repository;

import java.util.Map;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepositoryImpl.SubscriptionTypeReadableName;

public interface ConnectorApiKeyJpaRepositoryCustom {

  ConnectorApiKey saveOnlyAttributes(ConnectorApiKey connectorApiKey);

  Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType();

  void deleteConnectorApiKeyByIdProvider(String idProvider);
}
