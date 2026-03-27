package grafiosch.gtnet;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.repository.GlobalparametersJpaRepository;

/**
 * Utility for resolving the TCP connection timeout for a GTNet peer.
 * Checks the per-peer setting on GTNetConfig first, then falls back to the global default
 * from GlobalParameters (g.gnet.connection.timeout).
 */
public final class GTNetTimeoutHelper {

  private GTNetTimeoutHelper() {
  }

  /**
   * Resolves the connection timeout in seconds for a target GTNet peer.
   * First checks the per-peer setting, then falls back to the global parameter value.
   *
   * @param targetGTNet the remote peer (may be null)
   * @param globalparametersJpaRepository repository to read the global default from
   * @return timeout in seconds (per-peer if configured, otherwise the global default)
   */
  public static int resolveTimeout(GTNet targetGTNet, GlobalparametersJpaRepository globalparametersJpaRepository) {
    if (targetGTNet != null) {
      GTNetConfig config = targetGTNet.getGtNetConfig();
      if (config != null && config.getConnectionTimeout() != null) {
        return config.getConnectionTimeout();
      }
    }
    return globalparametersJpaRepository.getGTNetConnectionTimeout();
  }
}
