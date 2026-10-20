package grafiosch.security;

import grafiosch.dto.AccessTokenClientInfo;

/**
 * Implemented by the application that opens paths for personal access tokens. The library knows neither these paths
 * nor the public address of the instance, so the token dialog asks the application through this bean what to show the
 * user. Without such a bean the dialog shows no endpoint.
 */
public interface AccessTokenClientInfoProvider {

  /**
   * Returns the endpoint and a configuration example for a client.
   *
   * @return the connection details, never null
   */
  AccessTokenClientInfo getClientInfo();
}
