package grafioschtrader.service;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;

/**
 * Async service for pushing updated prices back to previously contacted PUSH_OPEN servers.
 *
 * This service is called after a GTNet lastprice exchange completes (Step 9) when the local
 * server is configured as AC_OPEN. It pushes prices that are newer than what each PUSH_OPEN
 * server originally sent, allowing those servers to update their push pools with fresher data.
 *
 * The push is fire-and-forget: the frontend receives the price update response immediately
 * without waiting for push operations to complete.
 */
@Component
public class GTNetLastpricePushService {

  private static final Logger log = LoggerFactory.getLogger(GTNetLastpricePushService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Asynchronously pushes updated prices to all previously contacted PUSH_OPEN servers.
   * This method runs in a separate thread and does not block the calling thread.
   *
   * For each contacted server, determines which prices are newer than what the server
   * originally sent and pushes only those updated prices.
   *
   * @param pushContext the context tracking which servers were contacted and their baseline timestamps
   * @param allSecurities all securities with final prices after the exchange
   * @param allCurrencypairs all currency pairs with final prices after the exchange
   */
  @Async
  public void asyncPushPricesToServers(PushOpenServerContext pushContext,
      List<Security> allSecurities, List<Currencypair> allCurrencypairs) {

    if (pushContext == null || !pushContext.hasServersToUpdate()) {
      return;
    }

    List<GTNet> serversToUpdate = pushContext.getContactedServers();
    log.info("Starting async push to {} PUSH_OPEN server(s)", serversToUpdate.size());

    // Get local GTNet entry for source identification
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    int totalPushed = 0;
    int serversContacted = 0;

    for (GTNet server : serversToUpdate) {
      try {
        LastpriceExchangeMsg pricesToPush = pushContext.getPricesToPushForServer(
            server, allSecurities, allCurrencypairs);

        if (!pricesToPush.isEmpty()) {
          boolean success = pushToServer(myGTNet, server, pricesToPush);
          if (success) {
            totalPushed += pricesToPush.getTotalCount();
            serversContacted++;
          }
        } else {
          log.debug("No newer prices to push to server {}", server.getDomainRemoteName());
        }
      } catch (Exception e) {
        log.warn("Failed to push prices to server {}: {}", server.getDomainRemoteName(), e.getMessage());
      }
    }

    log.info("Async push completed: {} prices pushed to {} server(s)", totalPushed, serversContacted);
  }

  /**
   * Pushes price updates to a single PUSH_OPEN server.
   *
   * @param myGTNet the local GTNet entry (source of the push)
   * @param server the target PUSH_OPEN server
   * @param pricesToPush the prices to push
   * @return true if push was successful
   */
  private boolean pushToServer(GTNet myGTNet, GTNet server, LastpriceExchangeMsg pricesToPush) {
    GTNetConfig config = server.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping unauthorized server for push: {}", server.getDomainRemoteName());
      return false;
    }

    // Build push message envelope
    MessageEnvelope pushEnvelope = new MessageEnvelope();
    pushEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
    pushEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    pushEnvelope.serverBusy = myGTNet.isServerBusy();
    pushEnvelope.messageCode = GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_SEL_C.getValue();
    pushEnvelope.timestamp = new Date();
    pushEnvelope.payload = objectMapper.valueToTree(pricesToPush);

    log.debug("Pushing {} securities and {} pairs to {}",
        pricesToPush.securities != null ? pricesToPush.securities.size() : 0,
        pricesToPush.currencypairs != null ? pricesToPush.currencypairs.size() : 0,
        server.getDomainRemoteName());

    // Send push message
    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(),
        server.getDomainRemoteName(),
        pushEnvelope);

    if (result.isFailed()) {
      if (result.httpError()) {
        log.warn("Push to {} returned HTTP error {}", server.getDomainRemoteName(), result.httpStatusCode());
      } else {
        log.warn("Push to {} failed - server unreachable", server.getDomainRemoteName());
      }
      return false;
    }

    // Log exchange statistics
    int entitiesSent = pricesToPush.getTotalCount();
    int acceptedCount = 0;

    // Parse acknowledgment response if available
    MessageEnvelope response = result.response();
    if (response != null && response.payload != null) {
      try {
        LastpriceExchangeMsg ackPayload = objectMapper.treeToValue(response.payload, LastpriceExchangeMsg.class);
        if (ackPayload != null && ackPayload.acceptedCount != null) {
          acceptedCount = ackPayload.acceptedCount;
        }
      } catch (Exception e) {
        log.debug("Could not parse push acknowledgment from {}", server.getDomainRemoteName());
      }
    }

    // Log as consumer (we're sending data, similar to provider logging pattern)
    gtNetExchangeLogService.logAsConsumer(server, GTNetExchangeKindType.LAST_PRICE,
        entitiesSent, acceptedCount, acceptedCount);

    log.debug("Successfully pushed {} prices to {}, {} accepted",
        entitiesSent, server.getDomainRemoteName(), acceptedCount);

    return true;
  }
}
