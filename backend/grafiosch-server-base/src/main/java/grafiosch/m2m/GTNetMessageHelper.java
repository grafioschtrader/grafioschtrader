package grafiosch.m2m;

import java.util.Date;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GlobalparametersJpaRepository;

/**
 * Helper class for GTNet message operations.
 *
 * Provides utility methods for common GTNet operations like retrieving the local GTNet entry ID
 * and sending ping messages to remote peers.
 */
public abstract class GTNetMessageHelper {

  /**
   * Retrieves the GTNet entry ID for this server, throwing if not configured.
   *
   * @param globalparametersJpaRepository the repository to query global parameters
   * @return the GTNet entry ID for this server
   * @throws IllegalArgumentException if GTNet entry ID is not configured
   */
  public static Integer getGTNetMyEntryIDOrThrow(GlobalparametersJpaRepository globalparametersJpaRepository) {
    Integer myIdGtNet = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myIdGtNet == null) {
      throw new IllegalArgumentException("Your machine does not have an entry!");
    }
    return myIdGtNet;
  }

  /**
   * Sends a ping message to a remote GTNet server and returns the status result.
   *
   * This method is used by the GTNetServerStatusCheckTask to check the reachability and busy status of remote peers
   * without going through the full message persistence flow.
   *
   * @param baseDataClient the HTTP client for M2M communication
   * @param sourceGTNet the local GTNet entry (provides serverBusy and domain info)
   * @param targetGTNet the remote GTNet entry to ping
   * @return SendResult containing reachability status and response
   */
  public static SendResult sendPingWithStatus(BaseDataClient baseDataClient, GTNet sourceGTNet, GTNet targetGTNet) {
    GTNetMessage gtNetMessagePing = new GTNetMessage(null, new Date(), SendReceivedType.SEND.getValue(), null,
        GNetCoreMessageCode.GT_NET_PING.getValue(), null, null);

    MessageEnvelope meRequest = new MessageEnvelope(sourceGTNet, gtNetMessagePing);

    String tokenRemote = targetGTNet.getGtNetConfig() != null ? targetGTNet.getGtNetConfig().getTokenRemote() : null;
    return baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), meRequest);
  }
}
