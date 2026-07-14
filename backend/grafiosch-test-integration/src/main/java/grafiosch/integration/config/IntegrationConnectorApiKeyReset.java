package grafiosch.integration.config;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafiosch.dto.SubscriptionTypeReadableName;
import grafiosch.repository.ConnectorApiKeyJpaRepositoryImpl.IConnectorApiKeyReset;

/** Application adapter for a host that deliberately has no market-data connectors. */
@Component
public class IntegrationConnectorApiKeyReset implements IConnectorApiKeyReset {

  @Override
  public void resetConnectorApiKey(String idProvider) {
    // No connector instances exist in the generic integration host.
  }

  @Override
  public Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType() {
    return Collections.emptyMap();
  }
}
