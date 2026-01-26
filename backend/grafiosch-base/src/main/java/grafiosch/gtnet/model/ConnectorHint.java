package grafiosch.gtnet.model;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Describes which connector type works for a security without exposing API keys or instance-specific configuration.
 * Used in GTNet security metadata exchange to provide hints about data sources that work for a security.
 */
@Schema(description = """
    Connector hint describing which connector type works for a security. Does not expose API keys or
    instance-specific configuration. Allows receiving instances to determine if they have compatible
    connectors configured.""")
public class ConnectorHint {

  /**
   * Capabilities a connector can provide for a security.
   */
  public enum ConnectorCapability {
    /** Historical end-of-day price data */
    HISTORY,
    /** Real-time or delayed intraday prices */
    INTRADAY,
    /** Dividend payment data */
    DIVIDEND,
    /** Stock split data */
    SPLIT
  }

  @Schema(description = "Connector family identifier (e.g., 'yahoo', 'finnhub', 'six', 'xetra')")
  private String connectorFamily;

  @Schema(description = "Set of capabilities this connector provides for the security")
  private Set<ConnectorCapability> capabilities;

  @Schema(description = "URL extension pattern that may be reusable on compatible instances")
  private String urlExtensionPattern;

  @Schema(description = "Whether this connector requires an API key to function")
  private boolean requiresApiKey;

  public ConnectorHint() {
  }

  public ConnectorHint(String connectorFamily, Set<ConnectorCapability> capabilities,
                       String urlExtensionPattern, boolean requiresApiKey) {
    this.connectorFamily = connectorFamily;
    this.capabilities = capabilities;
    this.urlExtensionPattern = urlExtensionPattern;
    this.requiresApiKey = requiresApiKey;
  }

  public String getConnectorFamily() {
    return connectorFamily;
  }

  public void setConnectorFamily(String connectorFamily) {
    this.connectorFamily = connectorFamily;
  }

  public Set<ConnectorCapability> getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(Set<ConnectorCapability> capabilities) {
    this.capabilities = capabilities;
  }

  public String getUrlExtensionPattern() {
    return urlExtensionPattern;
  }

  public void setUrlExtensionPattern(String urlExtensionPattern) {
    this.urlExtensionPattern = urlExtensionPattern;
  }

  public boolean isRequiresApiKey() {
    return requiresApiKey;
  }

  public void setRequiresApiKey(boolean requiresApiKey) {
    this.requiresApiKey = requiresApiKey;
  }
}
