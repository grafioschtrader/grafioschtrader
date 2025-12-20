package grafioschtrader.m2m;

import java.util.Date;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.m2m.client.BaseDataClient.SendResult;
import grafioschtrader.service.GlobalparametersService;

public abstract class GTNetMessageHelper {

  public static Integer getGTNetMyEntryIDOrThrow(GlobalparametersService globalparametersService) {
    Integer myIdGtNet = globalparametersService.getGTNetMyEntryID();
    if (myIdGtNet == null) {
      throw new IllegalArgumentException("Your machine does not have an entry!");
    }
    return globalparametersService.getGTNetMyEntryID();
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
        GTNetMessageCodeType.GT_NET_PING.getValue(), null, null);

    MessageEnvelope meRequest = new MessageEnvelope(sourceGTNet.getDomainRemoteName(), gtNetMessagePing,
        sourceGTNet.isServerBusy());

    String tokenRemote = targetGTNet.getGtNetConfig() != null ? targetGTNet.getGtNetConfig().getTokenRemote() : null;
    return baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), meRequest);
  }
}
