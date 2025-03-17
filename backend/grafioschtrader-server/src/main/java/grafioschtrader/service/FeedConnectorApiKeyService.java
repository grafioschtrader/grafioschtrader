package grafioschtrader.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.repository.ConnectorApiKeyJpaRepositoryImpl.IConnectorApiKeyReset;
import grafiosch.repository.ConnectorApiKeyJpaRepositoryImpl.SubscriptionTypeReadableName;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.types.SubscriptionType;

@Service
public class FeedConnectorApiKeyService implements IConnectorApiKeyReset {
  
  @Autowired(required = false)
  private List<IFeedConnector> feedConnectorbeans = new ArrayList<>();
  
  @Override
  public void resetConnectorApiKey(String idProvider) {
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
}
