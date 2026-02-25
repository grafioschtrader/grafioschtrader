package grafioschtrader.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.dto.SubscriptionTypeReadableName;
import grafiosch.repository.ConnectorApiKeyJpaRepositoryImpl.IConnectorApiKeyReset;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.generic.GenericFeedConnector;
import grafioschtrader.connector.instrument.generic.GenericFeedConnectorFactory;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.repository.GenericConnectorDefJpaRepository;
import grafioschtrader.types.SubscriptionType;

/**
 * Service for managing API key operations and subscription types for feed connectors. Provides functionality to reset
 * connector API keys and retrieve subscription type mappings for API key-based connectors, including generic connectors.
 */
@Service
public class FeedConnectorApiKeyService implements IConnectorApiKeyReset {

  @Autowired(required = false)
  private List<IFeedConnector> feedConnectorbeans = new ArrayList<>();

  @Autowired
  private GenericConnectorDefJpaRepository genericConnectorDefJpaRepository;

  @Autowired(required = false)
  private GenericFeedConnectorFactory genericFeedConnectorFactory;

  @Override
  public void resetConnectorApiKey(String idProvider) {
    Optional<IFeedConnector> found = feedConnectorbeans.stream()
        .filter(fc -> fc.getShortID().equals(idProvider)).findFirst();
    if (found.isPresent()) {
      IFeedConnector fc = found.get();
      if (fc instanceof BaseFeedApiKeyConnector) {
        ((BaseFeedApiKeyConnector) fc).resetConnectorApiKey();
      } else if (fc instanceof GenericFeedConnector && genericFeedConnectorFactory != null) {
        genericFeedConnectorFactory.reload();
      }
    } else if (genericFeedConnectorFactory != null) {
      genericFeedConnectorFactory.reload();
    }
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
    for (GenericConnectorDef def : genericConnectorDefJpaRepository.findByActivatedTrue()) {
      if (def.isNeedsApiKey() && !idSubTypeMap.containsKey(def.getShortId())) {
        idSubTypeMap.put(def.getShortId(), new SubscriptionTypeReadableName(def.getReadableName()));
      }
    }
    return idSubTypeMap;
  }
}
