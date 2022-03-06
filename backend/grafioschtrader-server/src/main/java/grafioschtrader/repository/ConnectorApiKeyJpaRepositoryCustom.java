package grafioschtrader.repository;

import java.util.Map;

import grafioschtrader.entities.ConnectorApiKey;
import grafioschtrader.repository.ConnectorApiKeyJpaRepositoryImpl.SubscriptionTypeReadableName;

public interface ConnectorApiKeyJpaRepositoryCustom {
  
  ConnectorApiKey saveOnlyAttributes(ConnectorApiKey connectorApiKey);
  
  Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType();
  
  void deleteConnectorApiKeyByIdProvider(String idProvider);
}
