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
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.common.DataHelper;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetModelHelper;
import grafioschtrader.gtnet.GTNetModelHelper.GTNetMsgRequest;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.gtnet.model.msg.ApplicationInfo;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.m2m.GTNetMessageHelper;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.service.GlobalparametersService;
import jakarta.transaction.Transactional;

public class GTNetJpaRepositoryImpl extends BaseRepositoryImpl<GTNet> implements GTNetJpaRepositoryCustom {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private BaseDataClient baseDataClient;

  @Override
  @Transactional
  public GTNetWithMessages getAllGTNetsWithMessages() {
    return new GTNetWithMessages(gtNetJpaRepository.findAll(), gtNetMessageJpaRepository
        .findAllByOrderByIdGtNetAscTimestampAsc().collect(Collectors.groupingBy(GTNetMessage::getIdGtNet)),
        globalparametersService.getGTNetMyEntryID());
  }

  @Override
  public GTNet saveOnlyAttributes(final GTNet gtNet, final GTNet existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    ApplicationInfo applicationInfo = baseDataClient.getActuatorInfo(gtNet.getDomainRemoteName());
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
      if (msgRequest.messageCode == GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C
          || msgRequest.messageCode == GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C) {
        return gtNetJpaRepository.findByAcceptEntityRequestOrAcceptLastpriceRequest(true, true);
      } else {
        return List.of();
      }
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
    List<GTNetMessage> gtNetMessages = new ArrayList<>();
    for (GTNet targetGTNet : gtNetList) {
      GTNetMessage gtNetMessage = gtNetMessageJpaRepository.saveMsg(
          new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), msgRequest.replyTo,
              msgRequest.messageCode.getValue(), msgRequest.message, msgRequest.gtNetMessageParamMap));
      MessageEnvelope meResponse = sendMessage(sourceGTNet, targetGTNet, gtNetMessage, null);
      if (gtNetMsgRequest.responseExpected) {
        gtNetMessages.add(new GTNetMessage(targetGTNet.getIdGtNet(), meResponse.timestamp,
            SendReceivedType.RECEIVED.getValue(), meResponse.souceIdForReply, meResponse.messageCode,
            meResponse.message, meResponse.gtNetMessageParamMap));
      }
      gtNetMessageJpaRepository.saveAll(gtNetMessages);
    }
  }

  private boolean hasOrCreateFirstContact(GTNet sourceGTNet, GTNet targetGTNet) {
    if (targetGTNet.getTokenThis() == null) {
      String tokenThis = DataHelper.generateGUID();
      Map<String, GTNetMessageParam> msgMap = convertPojoToMap(new FirstHandshakeMsg(tokenThis));
      GTNetMessage gtNetMessageRequest = gtNetMessageJpaRepository
          .saveMsg(new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), null,
              GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S.getValue(), null, msgMap));
      MessageEnvelope meResponse = sendMessage(sourceGTNet, targetGTNet, gtNetMessageRequest, sourceGTNet);
      GTNetMessage gtNetMessageResponse = gtNetMessageJpaRepository.save(new GTNetMessage(targetGTNet.getIdGtNet(),
          meResponse.timestamp, SendReceivedType.RECEIVED.getValue(), gtNetMessageRequest.getIdGtNetMessage(),
          meResponse.messageCode, meResponse.message, meResponse.gtNetMessageParamMap));
      return GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_ACCEPT_S == gtNetMessageResponse.getMessageCode();
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

  private Object convertMapToPojo(Class<?> clazz, Map<String, GTNetMessageParam> map) {
    final ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(map, clazz);
  }

  private MessageEnvelope sendMessage(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    if (gtNetMessage.getMessageCode() != GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S
        ? hasOrCreateFirstContact(sourceGTNet, targetGTNet)
        : true) {
      return sendMessage(targetGTNet.getTokenRemote(), targetGTNet.getDomainRemoteName(), gtNetMessage, payLoadObject);
    }
    return null;
  }

  private MessageEnvelope sendMessage(String tokenRemote, String domainRemoteName, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    MessageEnvelope meRequest = new MessageEnvelope(domainRemoteName, gtNetMessage);
    if (payLoadObject != null) {
      meRequest.payload = objectMapper.convertValue(payLoadObject, JsonNode.class);
    }
    return baseDataClient.sendToMsg(tokenRemote, domainRemoteName, meRequest);
  }

  private MessageEnvelope sendPing(String tokenRemote, String domainRemoteName) {
    GTNetMessage gtNetMessagePing = new GTNetMessage(null, new Date(), SendReceivedType.SEND.getValue(), null,
        GTNetMessageCodeType.GT_NET_PING.getValue(), null, null);
    return sendMessage(tokenRemote, domainRemoteName, gtNetMessagePing, null);
  }

  // Receive Message
  ///////////////////////////////////////////////////////////////////////
  @Override
  public MessageEnvelope getMsgResponse(MessageEnvelope me) throws Exception {
    MessageEnvelope meResponse = null;
    GTNet myGTNet = gtNetJpaRepository
        .getReferenceById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService));
    switch (GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(me.messageCode)) {
    case GT_NET_PING:
      meResponse = getGTNetPingResponse(myGTNet);
      break;

    case GT_NET_FIRST_HANDSHAKE_S:
      meResponse = checkHandshake(myGTNet, me);
      break;
    case GT_NET_UPDATE_SERVERLIST_SEL_C:
      break;

    default:
      break;

    }
    return meResponse;
  }

  private MessageEnvelope getGTNetPingResponse(GTNet myGTnet) {
    GTNetMessage msgRespone = new GTNetMessage(null, new Date(), SendReceivedType.ANSWER.getValue(), null,
        GTNetMessageCodeType.GT_NET_PING.getValue(), null, null);
    MessageEnvelope meResponse = new MessageEnvelope(myGTnet.getDomainRemoteName(), msgRespone);
    return meResponse;
  }

  private MessageEnvelope checkHandshake(GTNet myGTNet, MessageEnvelope meRequest)
      throws JsonProcessingException, IllegalArgumentException {
    GTNetMsgRequest gtNetMsgRequest = GTNetModelHelper
        .getMsgClassByMessageCode(GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S);
    FirstHandshakeMsg firstHandshakeMsg = (FirstHandshakeMsg) convertMapToPojo(gtNetMsgRequest.model,
        meRequest.gtNetMessageParamMap);
    GTNet remoteGTNet = objectMapper.treeToValue(meRequest.payload, GTNet.class);

    // TODO add Ping to check if the remote machine is reachable
    remoteGTNet = addGTNetRemoteWhenNotExists(remoteGTNet, firstHandshakeMsg.tokenThis);

    return null;
  }

  private GTNet addGTNetRemoteWhenNotExists(GTNet gtNetRemote, String remoteToken) {
    GTNet gtNetRemoteExisting = gtNetJpaRepository.findByDomainRemoteName(gtNetRemote.getDomainRemoteName());
    if (gtNetRemoteExisting == null) {
      gtNetRemoteExisting = gtNetRemote;
    }
    gtNetRemoteExisting.setTokenRemote(remoteToken);
    return gtNetJpaRepository.save(gtNetRemoteExisting);
  }

}
