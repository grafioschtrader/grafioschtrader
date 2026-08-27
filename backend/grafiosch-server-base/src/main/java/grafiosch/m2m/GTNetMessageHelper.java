package grafiosch.m2m;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetTime;
import grafiosch.gtnet.GTNetTimeoutHelper;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GlobalparametersJpaRepository;

/**
 * Helper class for GTNet message operations.
 *
 * Provides utility methods for common GTNet operations like retrieving the local GTNet entry ID and sending ping
 * messages to remote peers.
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
   * Builds the envelope of a liveness ping.
   *
   * <p>
   * The ping message is never persisted, so the envelope names no sender-local message. That is why {@code GT_NET_PING}
   * declares itself a transient send in the protocol registry — a receiver that demanded the id would refuse every ping
   * as an invalid envelope. Separate from the send so that the envelope the receiver actually gets can be validated in
   * a test without a peer.
   * </p>
   *
   * @param sourceGTNet the local GTNet entry, which supplies the domain and the busy flag
   * @return the envelope to post to the peer
   */
  public static MessageEnvelope buildPingEnvelope(GTNet sourceGTNet) {
    GTNetMessage gtNetMessagePing = new GTNetMessage(null, GTNetTime.now(), SendReceivedType.SEND.getValue(), null,
        GNetCoreMessageCode.GT_NET_PING.getValue(), null, null);
    return new MessageEnvelope(sourceGTNet, gtNetMessagePing);
  }

  /**
   * Sends a ping message to a remote GTNet server and returns the status result.
   *
   * This method is used by the GTNetServerStatusCheckTask to check the reachability and busy status of remote peers
   * without going through the full message persistence flow.
   *
   * @param baseDataClient the HTTP client for M2M communication
   * @param sourceGTNet    the local GTNet entry (provides serverBusy and domain info)
   * @param targetGTNet    the remote GTNet entry to ping
   * @return SendResult containing reachability status and response
   */
  public static SendResult sendPingWithStatus(BaseDataClient baseDataClient, GTNet sourceGTNet, GTNet targetGTNet,
      GlobalparametersJpaRepository globalparametersJpaRepository) {
    MessageEnvelope meRequest = buildPingEnvelope(sourceGTNet);

    String tokenRemote = targetGTNet.getGtNetConfig() != null ? targetGTNet.getGtNetConfig().getTokenRemote() : null;
    return baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), meRequest,
        GTNetTimeoutHelper.resolveTimeout(targetGTNet, globalparametersJpaRepository));
  }
}
