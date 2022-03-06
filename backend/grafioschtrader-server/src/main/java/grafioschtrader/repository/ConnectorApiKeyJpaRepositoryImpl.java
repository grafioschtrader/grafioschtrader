package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.ConnectorApiKey;
import grafioschtrader.entities.User;
import grafioschtrader.types.SubscriptionType;

public class ConnectorApiKeyJpaRepositoryImpl implements ConnectorApiKeyJpaRepositoryCustom {

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  @Autowired(required = false)
  private List<IFeedConnector> feedConnectorbeans = new ArrayList<>();

  @Override
  public ConnectorApiKey saveOnlyAttributes(ConnectorApiKey connectorApiKey) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.isAdmin(user)) {
      connectorApiKey = connectorApiKeyJpaRepository.save(connectorApiKey);
      resetConnectorApiKey(connectorApiKey.getIdProvider());
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    return connectorApiKey;
  }

  private void resetConnectorApiKey(String idProvider) {
    feedConnectorbeans.stream().filter(fc -> fc.getShortID().equals(idProvider)).findFirst()
        .ifPresent(fc -> ((BaseFeedApiKeyConnector) fc).resetConnectorApiKey());
  }

  @Override
  public Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType() {
    Map<String, SubscriptionTypeReadableName> idSubTypeMap = new HashMap<>();
    for (IFeedConnector feedConnector : feedConnectorbeans) {
      if (feedConnector instanceof BaseFeedApiKeyConnector) {
        SubscriptionTypeReadableName strn = new SubscriptionTypeReadableName(feedConnector.getReadableName());
        for (SubscriptionType subscriptionType : SubscriptionType.values()) {
          String subTypeStripped = subscriptionType.name().toLowerCase().replaceAll("_", "");
          if (subTypeStripped.startsWith(feedConnector.getShortID())) {
            strn.subscriptionTypes.add(subscriptionType);
          }
        }
        idSubTypeMap.put(feedConnector.getShortID(), strn);
      }
    }
    return idSubTypeMap;
  }

  public static class SubscriptionTypeReadableName {
    public String readableName;
    public List<SubscriptionType> subscriptionTypes = new ArrayList<>();

    public SubscriptionTypeReadableName(String readableName) {
      this.readableName = readableName;
    }
  }

  @Override
  public void deleteConnectorApiKeyByIdProvider(String idProvider) {
    connectorApiKeyJpaRepository.deleteById(idProvider);
    this.resetConnectorApiKey(idProvider);
    
  }

}
