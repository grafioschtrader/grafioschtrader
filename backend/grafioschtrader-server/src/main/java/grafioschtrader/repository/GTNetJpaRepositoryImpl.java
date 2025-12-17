package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.common.DataHelper;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetModelHelper;
import grafioschtrader.gtnet.GTNetModelHelper.GTNetMsgRequest;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.GTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageHandlerRegistry;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.m2m.GTNetMessageHelper;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.service.GlobalparametersService;
import jakarta.transaction.Transactional;

public class GTNetJpaRepositoryImpl extends BaseRepositoryImpl<GTNet> implements GTNetJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetJpaRepositoryImpl.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  @Lazy
  private GTNetMessageHandlerRegistry handlerRegistry;

  @Override
  @Transactional
  public GTNetWithMessages getAllGTNetsWithMessages() {
    List<GTNetMessage> gtNetMessages = gtNetMessageJpaRepository.findAllByOrderByIdGtNetAscTimestampAsc();
    return new GTNetWithMessages(gtNetJpaRepository.findAll(),
        gtNetMessages.stream().collect(Collectors.groupingBy(GTNetMessage::getIdGtNet)),
        globalparametersService.getGTNetMyEntryID());
  }

  @Override
  public GTNet saveOnlyAttributes(final GTNet gtNet, final GTNet existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    // Validate remote URL is reachable
    baseDataClient.getActuatorInfo(gtNet.getDomainRemoteName());
    GTNet gtNetNew = RepositoryHelper.saveOnlyAttributes(gtNetJpaRepository, gtNet, existingEntity,
        updatePropertyLevelClasses);
    if (isDomainNameThisMachine(gtNet.getDomainRemoteName())) {
      globalparametersService.saveGTNetMyEntryID(gtNetNew.getIdGtNet());
    }
    return gtNetNew;
  }

  static boolean isDomainNameThisMachine(String domainName)
      throws SocketException, UnknownHostException, URISyntaxException {
    URI uri = new URI(domainName);
    String host = uri.getHost();
    InetAddress[] searchAddr = InetAddress.getAllByName(host);
    Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
    for (NetworkInterface netint : Collections.list(nets)) {
      if (!netint.isLoopback()
          && !Collections.disjoint(Collections.list(netint.getInetAddresses()), Arrays.asList(searchAddr))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public GTNetWithMessages submitMsg(MsgRequest msgRequest) {
    GTNetMsgRequest gtNetMsgRequest = GTNetModelHelper.getMsgClassByMessageCode(msgRequest.messageCode);

    // Object model = objectMapper.convertValue(msgRequest.gtNetMessageParamMap,
    // gtNetMsgRequest.model);
    // TODO check model integrity
    List<GTNet> gtNetList = getTargetDomains(msgRequest);
    sendAndSaveMsg(
        gtNetJpaRepository.findById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService)).orElseThrow(),
        gtNetList, gtNetMsgRequest, msgRequest);
    return this.getAllGTNetsWithMessages();
  }

  private List<GTNet> getTargetDomains(MsgRequest msgRequest) {
    if (msgRequest.messageCode.name().contains(GTNetModelHelper.MESSAGE_TO_ALL)) {
      // All broadcast messages go to servers that have accepted entity or lastprice requests
      return switch (msgRequest.messageCode) {
      case GT_NET_OFFLINE_ALL_C, GT_NET_ONLINE_ALL_C, GT_NET_BUSY_ALL_C, GT_NET_RELEASED_BUSY_ALL_C,
          GT_NET_MAINTENANCE_ALL_C, GT_NET_OPERATION_DISCONTINUED_ALL_C ->
        gtNetJpaRepository.findByAcceptEntityRequestOrAcceptLastpriceRequest(true, true);
      default -> List.of();
      };
    } else {
      List<GTNet> gtNetList = new ArrayList<>();
      gtNetJpaRepository.findById(msgRequest.idGTNetTargetDomain).stream().forEach(n -> gtNetList.add(n));
      return gtNetList;
    }
  }

  // Send Message
  ///////////////////////////////////////////////////////////////////////
  /**
   * Message is created and send to the remote servers.
   *
   * @param gtNetList
   * @param msgRequest
   */
  private void sendAndSaveMsg(GTNet sourceGTNet, List<GTNet> gtNetList, GTNetMsgRequest gtNetMsgRequest,
      MsgRequest msgRequest) {
    for (GTNet targetGTNet : gtNetList) {
      GTNetMessage gtNetMessage = gtNetMessageJpaRepository.saveMsg(
          new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), msgRequest.replyTo,
              msgRequest.messageCode.getValue(), msgRequest.message, msgRequest.gtNetMessageParamMap));
      MessageEnvelope meResponse = sendMessage(sourceGTNet, targetGTNet, gtNetMessage, null);
      if (gtNetMsgRequest.responseExpected && meResponse != null) {
        // Save received response with idSourceGtNetMessage from remote and replyTo pointing to our request
        GTNetMessage responseMsg = new GTNetMessage(targetGTNet.getIdGtNet(), meResponse.timestamp,
            SendReceivedType.RECEIVED.getValue(), gtNetMessage.getIdGtNetMessage(), meResponse.messageCode,
            meResponse.message, meResponse.gtNetMessageParamMap);
        responseMsg.setIdSourceGtNetMessage(meResponse.idSourceGtNetMessage);
        gtNetMessageJpaRepository.save(responseMsg);
      }
    }
  }

  private boolean hasOrCreateFirstContact(GTNet sourceGTNet, GTNet targetGTNet) {
    if (targetGTNet.getTokenRemote() == null) {
      // Generate the token that the remote will use to authenticate back to us
      String tokenForRemote = DataHelper.generateGUID();
      Map<String, GTNetMessageParam> msgMap = convertPojoToMap(new FirstHandshakeMsg(tokenForRemote));
      GTNetMessage gtNetMessageRequest = gtNetMessageJpaRepository
          .saveMsg(new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), null,
              GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S.getValue(), null, msgMap));
      // Send our GTNet entity in the payload so the receiver can register us
      MessageEnvelope meResponse = sendMessage(sourceGTNet, targetGTNet, gtNetMessageRequest, sourceGTNet);
      if (meResponse != null && meResponse.messageCode == GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue()) {
        // Extract the token they gave us from their response
        FirstHandshakeMsg responseMsgData = convertMapToPojo(FirstHandshakeMsg.class, meResponse.gtNetMessageParamMap);
        // Store their token (what we use to call them) as tokenRemote
        targetGTNet.setTokenRemote(responseMsgData.tokenThis);
        // Store our token (what they use to call us) as tokenThis
        targetGTNet.setTokenThis(tokenForRemote);
        gtNetJpaRepository.save(targetGTNet);

        // Save received response message with idSourceGtNetMessage from remote and replyTo pointing to our request
        GTNetMessage gtNetMessageResponse = new GTNetMessage(targetGTNet.getIdGtNet(),
            meResponse.timestamp, SendReceivedType.RECEIVED.getValue(), gtNetMessageRequest.getIdGtNetMessage(),
            meResponse.messageCode, meResponse.message, meResponse.gtNetMessageParamMap);
        gtNetMessageResponse.setIdSourceGtNetMessage(meResponse.idSourceGtNetMessage);
        gtNetMessageJpaRepository.save(gtNetMessageResponse);
        return true;
      }
      return false;
    } else {
      return true;
    }
  }

  private Map<String, GTNetMessageParam> convertPojoToMap(Object msgPojo) {
    Map<String, String> fhMap = objectMapper.convertValue(msgPojo, new TypeReference<Map<String, String>>() {
    });
    Map<String, GTNetMessageParam> msgMap = new HashMap<>();
    for (Map.Entry<String, String> entry : fhMap.entrySet()) {
      msgMap.put(entry.getKey(), new GTNetMessageParam(entry.getValue()));
    }
    return msgMap;
  }

  private <T> T convertMapToPojo(Class<T> clazz, Map<String, GTNetMessageParam> map) {
    Map<String, String> valueMap = map.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getParamValue()));
    return objectMapper.convertValue(valueMap, clazz);
  }

  private MessageEnvelope sendMessage(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    if (gtNetMessage.getMessageCode() != GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S
        ? hasOrCreateFirstContact(sourceGTNet, targetGTNet)
        : true) {
      return sendMessage(sourceGTNet.getDomainRemoteName(), targetGTNet.getTokenRemote(), targetGTNet.getDomainRemoteName(), gtNetMessage, payLoadObject);
    }
    return null;
  }

  private MessageEnvelope sendMessage(String domainSourceName, String tokenRemote, String domainRemoteName, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    MessageEnvelope meRequest = new MessageEnvelope(domainSourceName, gtNetMessage);
    if (payLoadObject != null) {
      meRequest.payload = objectMapper.convertValue(payLoadObject, JsonNode.class);
    }
    return baseDataClient.sendToMsg(tokenRemote, domainRemoteName, meRequest);
  }

  private MessageEnvelope sendPing(String domainSourceName, String tokenRemote, String domainRemoteName) {
    GTNetMessage gtNetMessagePing = new GTNetMessage(null, new Date(), SendReceivedType.SEND.getValue(), null,
        GTNetMessageCodeType.GT_NET_PING.getValue(), null, null);
    return sendMessage(domainSourceName, tokenRemote, domainRemoteName, gtNetMessagePing, null);
  }

  // Receive Message
  ///////////////////////////////////////////////////////////////////////
  /**
   * Processes an incoming GTNet message and returns the appropriate response.
   *
   * This method delegates to the handler registry, which routes the message to the appropriate handler based on the
   * message code. The handler determines whether to return an immediate response, await manual handling, or process
   * without response (for announcements).
   *
   * @param me the incoming message envelope
   * @return the response envelope, or null if no immediate response is needed
   */
  @Override
  public MessageEnvelope getMsgResponse(MessageEnvelope me) throws Exception {
    GTNet myGTNet = gtNetJpaRepository
        .getReferenceById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService));

    GTNetMessageCodeType messageCode = GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(me.messageCode);
    if (messageCode == null) {
      log.warn("Unknown message code received: {}", me.messageCode);
      return buildErrorResponse(myGTNet, "UNKNOWN_MESSAGE_CODE", "Unknown message code: " + me.messageCode);
    }

    // Check if handler is registered
    if (!handlerRegistry.hasHandler(messageCode)) {
      log.warn("No handler registered for message code: {}", messageCode);
      return buildErrorResponse(myGTNet, "NO_HANDLER", "No handler for message code: " + messageCode);
    }

    // Look up remote GTNet (may be null for first handshake)
    GTNet remoteGTNet = gtNetJpaRepository.findByDomainRemoteName(me.sourceDomain);

    // Look up auto-response rules for this message code
    Optional<GTNetMessageAnswer> autoResponseRules = gtNetMessageAnswerJpaRepository.findById(me.messageCode);

    // Build context for the handler
    GTNetMessageContext context = new GTNetMessageContext(myGTNet, remoteGTNet, me, autoResponseRules.orElse(null),
        objectMapper);

    // Get the handler and process the message
    GTNetMessageHandler handler = handlerRegistry.getHandler(messageCode);
    HandlerResult result = handler.handle(context);

    // Process the result
    return switch (result) {
    case HandlerResult.ImmediateResponse r -> r.response();
    case HandlerResult.AwaitingManualResponse r -> {
      log.info("Message {} from {} awaiting manual response", messageCode, me.sourceDomain);
      yield null;
    }
    case HandlerResult.NoResponseNeeded r -> null;
    case HandlerResult.ProcessingError e -> {
      log.error("Error processing message {}: {} - {}", messageCode, e.errorCode(), e.message());
      yield buildErrorResponse(myGTNet, e.errorCode(), e.message());
    }
    };
  }

  /**
   * Builds an error response envelope.
   */
  private MessageEnvelope buildErrorResponse(GTNet myGTNet, String errorCode, String message) {
    GTNetMessage errorMsg = new GTNetMessage(null, new Date(), SendReceivedType.ANSWER.getValue(), null,
        GTNetMessageCodeType.GT_NET_PING.getValue(), message, null);
    errorMsg.setErrorMsgCode(errorCode);
    MessageEnvelope errorEnvelope = new MessageEnvelope(myGTNet.getDomainRemoteName(), errorMsg);
    return errorEnvelope;
  }

  @Override
  public void validateIncomingToken(String sourceDomain, String authToken) {
    if (authToken == null || authToken.isBlank()) {
      throw new SecurityException("Missing authentication token");
    }

    GTNet remoteGTNet = gtNetJpaRepository.findByDomainRemoteName(sourceDomain);
    if (remoteGTNet == null) {
      throw new SecurityException("Unknown source domain: " + sourceDomain);
    }

    String expectedToken = remoteGTNet.getTokenThis();
    if (expectedToken == null || !expectedToken.equals(authToken)) {
      throw new SecurityException("Invalid authentication token for domain: " + sourceDomain);
    }
  }

}
