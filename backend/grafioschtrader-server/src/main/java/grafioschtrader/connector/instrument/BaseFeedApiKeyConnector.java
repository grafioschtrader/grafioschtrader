package grafioschtrader.connector.instrument;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.ConnectorApiKey;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.types.SubscriptionType;

public abstract class BaseFeedApiKeyConnector extends BaseFeedConnector {

  protected final static String ERROR_API_KEY_REPLACEMENT = "???";

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  private ConnectorApiKey connectorApiKey;

  protected BaseFeedApiKeyConnector(final Map<FeedSupport, FeedIdentifier[]> supportedFeed, final String id,
      final String readableNameKey, String regexStrPattern, EnumSet<UrlCheck> urlCheckSet) {
    super(supportedFeed, id, readableNameKey, regexStrPattern, urlCheckSet);
  }

  /**
   * <p>
   * Before the URL is checked. It should be checked whether the data provider's
   * subscription covers the corresponding service at all. In the event of an
   * error, an error should be thrown.
   * </p>
   * {@inheritDoc}
   */
  @Override
  public <S extends Securitycurrency<S>> void checkAndClearSecuritycurrencyUrlExtend(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport) {
    hasAPISubscriptionSupport(securitycurrency, feedSupport);
    super.checkAndClearSecuritycurrencyUrlExtend(securitycurrency, feedSupport);
  }

  public <S extends Securitycurrency<S>> void hasAPISubscriptionSupport(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport) {
  }

  @Override
  protected String hideApiKeyForError(String url) {
    return url.replaceFirst("(^.*)(\\?.*$)", "$1");
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
