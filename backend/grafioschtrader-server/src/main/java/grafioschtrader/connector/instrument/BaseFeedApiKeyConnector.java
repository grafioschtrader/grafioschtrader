package grafioschtrader.connector.instrument;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.ConnectorApiKey;
import grafioschtrader.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.types.SubscriptionType;

public abstract class BaseFeedApiKeyConnector extends BaseFeedConnector {

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  private ConnectorApiKey connectorApiKey;

  protected BaseFeedApiKeyConnector(final Map<FeedSupport, FeedIdentifier[]> supportedFeed, final String id,
      final String readableNameKey, String regexStrPattern) {
    super(supportedFeed, id, readableNameKey, regexStrPattern);
  }

  @Override
  public boolean isActivated() {
    getConnectorApiKey();
    return connectorApiKey != null;
  }

  public void resetConnectorApiKey() {
    connectorApiKey = null;
  }

  protected String getApiKey() {
    getConnectorApiKey();
    return connectorApiKey == null ? null : connectorApiKey.getApiKey();
  }

  protected SubscriptionType getSubscriptionType() {
    getConnectorApiKey();
    return connectorApiKey.getSubscriptionType();
  }

  private void getConnectorApiKey() {
    if (connectorApiKey == null) {
      Optional<ConnectorApiKey> connectorApiKeyOpt = connectorApiKeyJpaRepository.findById(getShortID());
      if (connectorApiKeyOpt.isPresent()) {
        connectorApiKey = connectorApiKeyOpt.get();
      }
    }
  }
}
