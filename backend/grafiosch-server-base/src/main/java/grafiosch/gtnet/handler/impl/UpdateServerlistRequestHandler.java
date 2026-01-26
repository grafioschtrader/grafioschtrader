package grafiosch.gtnet.handler.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.handler.AbstractRequestHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.ValidationResult;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetJpaRepository;

/**
 * Handler for GT_NET_UPDATE_SERVERLIST_SEL_RR_C messages.
 *
 * Processes requests from remote servers to share the local server list. When accepted, the response includes the list
 * of known GTNet servers that have spreadCapability enabled.
 *
 * Auto-accept behavior: If this remote was previously granted server list access (serverlistAccessGranted = true in
 * GTNetConfig) and has not since revoked, the request is automatically accepted without requiring manual approval or
 * GTNetMessageAnswer rules.
 */
@Component
public class UpdateServerlistRequestHandler extends AbstractRequestHandler {

  private static final Logger log = LoggerFactory.getLogger(UpdateServerlistRequestHandler.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_SEL_RR_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    // Must be from a known remote
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE",
          "Server list request from unknown domain - handshake required first");
    }
    return ValidationResult.ok();
  }

  @Override
  protected Optional<? extends GTNetMessageCode> checkPriorApproval(GTNetMessageContext context) {
    // Check if this remote was previously granted access
    GTNet remoteGTNet = context.getRemoteGTNet();
    GTNetConfig config = remoteGTNet != null ? remoteGTNet.getGtNetConfig() : null;
    if (config != null && config.isServerlistAccessGranted()) {
      log.info("Auto-accepting server list request from {} - previously granted access", context.getSourceDomain());
      return Optional.of(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S);
    }
    return Optional.empty();
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // No side effects on request receipt
  }

  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCode responseCode,
      GTNetMessage storedRequest) {
    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S.getValue()) {
      // Grant server list access to this remote
      GTNet remoteGTNet = context.getRemoteGTNet();
      GTNetConfig config = remoteGTNet.getGtNetConfig();
      if (config != null && !config.isServerlistAccessGranted()) {
        config.setServerlistAccessGranted(true);
        saveGTNetConfig(config);
        log.info("Granted server list access to {}", context.getSourceDomain());
      }
    }
  }

  @Override
  protected MessageEnvelope buildResponse(GTNetMessageContext context, GTNetMessageCode responseCode,
      String message, GTNetMessage originalRequest) {
    GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message, null, originalRequest);
    MessageEnvelope envelope = createResponseEnvelope(context, responseMsg);

    // Include server list in payload for ACCEPT responses
    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S.getValue()) {
      List<GTNetPublicDTO> serverList = buildShareableServerList(context);
      envelope.payload = objectMapper.valueToTree(serverList);
      log.info("Including {} servers in server list response to {}", serverList.size(), context.getSourceDomain());
    }

    return envelope;
  }

  /**
   * Builds the list of servers to share with the requester.
   *
   * @param context the message context
   * @return list of GTNetPublicDTO representing shareable servers
   */
  private List<GTNetPublicDTO> buildShareableServerList(GTNetMessageContext context) {
    Integer excludeId = context.getRemoteGTNet().getIdGtNet();
    List<GTNet> shareableServers = gtNetJpaRepository.findShareableServers(excludeId);
    return shareableServers.stream().map(GTNetPublicDTO::new).collect(Collectors.toList());
  }
}
