package grafioschtrader.repository;

import grafioschtrader.entities.ConnectorApiKey;

public interface ConnectorApiKeyJpaRepositoryCustom {
  
  ConnectorApiKey saveOnlyAttributes(ConnectorApiKey connectorApiKey);
}
