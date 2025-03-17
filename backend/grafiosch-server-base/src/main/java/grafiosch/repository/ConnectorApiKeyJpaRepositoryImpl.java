package grafiosch.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.ConnectorApiKey;
import grafiosch.entities.User;
import grafiosch.types.ISubscriptionType;

public class ConnectorApiKeyJpaRepositoryImpl implements ConnectorApiKeyJpaRepositoryCustom {

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  @Autowired
  private IConnectorApiKeyReset connectorApiKeyReset;

  @Override
  public ConnectorApiKey saveOnlyAttributes(ConnectorApiKey connectorApiKey) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.isAdmin(user)) {
      connectorApiKey = connectorApiKeyJpaRepository.save(connectorApiKey);
      connectorApiKeyReset.resetConnectorApiKey(connectorApiKey.getIdProvider());
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return connectorApiKey;
  }

  @Override
  public Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType() {
    return connectorApiKeyReset.getFeedSubscriptionType();
  }

  public static class SubscriptionTypeReadableName {
    public String readableName;
    public List<ISubscriptionType> subscriptionTypes = new ArrayList<>();

    public SubscriptionTypeReadableName(String readableName) {
      this.readableName = readableName;
    }
  }

  @Override
  public void deleteConnectorApiKeyByIdProvider(String idProvider) {
    connectorApiKeyJpaRepository.deleteById(idProvider);
    connectorApiKeyReset.resetConnectorApiKey(idProvider);

  }

  public static interface IConnectorApiKeyReset {
    void resetConnectorApiKey(String idProvider);

    public Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType();
  }

}
