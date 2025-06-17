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
import io.swagger.v3.oas.annotations.media.Schema;

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

  @Override
  public void deleteConnectorApiKeyByIdProvider(String idProvider) {
    connectorApiKeyJpaRepository.deleteById(idProvider);
    connectorApiKeyReset.resetConnectorApiKey(idProvider);
  }

  @Schema(description = """
      Encapsulates subscription type information in a format suitable for user interface display and selection.
      It combines human-readable names with the available subscription type options for a specific provider.""")
  public static class SubscriptionTypeReadableName {
    @Schema(description = "Human-readable display name for the provider or subscription category.")
    public String readableName;

    @Schema(description = "List of available subscription types for this provider.")
    public List<ISubscriptionType> subscriptionTypes = new ArrayList<>();

    public SubscriptionTypeReadableName(String readableName) {
      this.readableName = readableName;
    }
  }

  public static interface IConnectorApiKeyReset {
    /**
     * If the API key has been deleted or changed, this must be communicated to the corresponding implementation of the
     * provider. So that the provider can read it in again.
     * 
     * @param idProvider the provider identifier for the connector to reset
     */
    void resetConnectorApiKey(String idProvider);

    /**
     * Retrieves comprehensive subscription type information for all providers.
     * 
     * <p>
     * This method returns the authoritative mapping of provider identifiers to their available subscription types and
     * display information. The data is used throughout the application for configuration, validation, and user
     * interface purposes.
     * </p>
     * 
     * @return map of provider IDs to subscription type information
     */
    Map<String, SubscriptionTypeReadableName> getFeedSubscriptionType();
  }

}
