package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.entities.ConnectorApiKey;
import grafioschtrader.entities.User;

public class ConnectorApiKeyJpaRepositoryImpl implements ConnectorApiKeyJpaRepositoryCustom  {

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;
  
  
  @Override
  public ConnectorApiKey saveOnlyAttributes(ConnectorApiKey connectorApiKey) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.isAdmin(user)) {
      connectorApiKeyJpaRepository.save(connectorApiKey);
    }
    throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
  }

}
