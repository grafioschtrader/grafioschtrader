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

import grafioschtrader.common.DataHelper;
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
import jakarta.transaction.Transactional;

public class GTNetJpaRepositoryImpl extends BaseRepositoryImpl<GTNet> implements GTNetJpaRepositoryCustom {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private BaseDataClient baseDataClient;

  @Override
  @Transactional
  public GTNetWithMessages getAllGTNetsWithMessages() {
    return new GTNetWithMessages(gtNetJpaRepository.findAll(), gtNetMessageJpaRepository
        .findAllByOrderByIdGtNetAscTimestampAsc().collect(Collectors.groupingBy(GTNetMessage::getIdGtNet)),
        globalparametersJpaRepository.getGTNetMyEntryID());
  }

  

  @Override
  public GTNet saveOnlyAttributes(final GTNet gtNet, final GTNet existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    ApplicationInfo applicationInfo = baseDataClient.getActuatorInfo(gtNet.getDomainRemoteName());
    GTNet gtNetNew = RepositoryHelper.saveOnlyAttributes(gtNetJpaRepository, gtNet, existingEntity,
        updatePropertyLevelClasses);
    if (isDomainNameThisMachine(gtNet.getDomainRemoteName())) {
      globalparametersJpaRepository.saveGTNetMyEntryID(gtNetNew.getIdGtNet());
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
    Object model = objectMapper.convertValue(msgRequest.gtNetMessageParamMap, gtNetMsgRequest.model);
    // TODO check model integrity
    List<GTNet> gtNetList = gtNetJpaRepository.findAllById(msgRequest.idGTNetTargetDomains);
    sendAndSaveMsg(
        gtNetJpaRepository.getReferenceById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository)),
        gtNetList, gtNetMsgRequest, msgRequest);
    return this.getAllGTNetsWithMessages();
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
      Map<String, GTNetMessageParam> msgMap = convertPojoToMap(new FirstHandshakeMsg(DataHelper.generateGUID()));
      GTNetMessage gtNetMessageRequest = gtNetMessageJpaRepository
          .saveMsg(new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), null,
              GTNetMessageCodeType.GTNET_FIRST_HANDSHAKE_S.getValue(), null, msgMap));
      MessageEnvelope meResponse = sendMessage(sourceGTNet, targetGTNet, gtNetMessageRequest, sourceGTNet);
      GTNetMessage gtNetMessageResponse = gtNetMessageJpaRepository.save(new GTNetMessage(targetGTNet.getIdGtNet(),
          meResponse.timestamp, SendReceivedType.RECEIVED.getValue(), gtNetMessageRequest.getIdGtNetMessage(),
          meResponse.messageCode, meResponse.message, meResponse.gtNetMessageParamMap));
      return GTNetMessageCodeType.GTNET_FIRST_HANDSHAKE_ACCEPT_S == gtNetMessageResponse.getMessageCode();
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

  private MessageEnvelope sendMessage(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage, Object payLoadObject) {
    if (gtNetMessage.getMessageCode() == GTNetMessageCodeType.GTNET_FIRST_HANDSHAKE_REJECT_S
        ? hasOrCreateFirstContact(sourceGTNet, targetGTNet)
        : true) {
      MessageEnvelope meRequest = new MessageEnvelope(sourceGTNet.getDomainRemoteName(), gtNetMessage);
      if(payLoadObject != null) {
        meRequest.payload = objectMapper.convertValue(payLoadObject, JsonNode.class);
      }
      
      return baseDataClient.sendToMsg(targetGTNet.getTokenRemote(), targetGTNet.getDomainRemoteName(), meRequest);
    }
    return null;
  }
  
  
  // Receive Message
  ///////////////////////////////////////////////////////////////////////
  @Override
  public MessageEnvelope getMsgResponse(MessageEnvelope me)  {
    MessageEnvelope meResponse = null;
    GTNet myGTNet = gtNetJpaRepository.getReferenceById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository));
    switch(GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(me.messageCode)) {
    case GTNET_PING:
      meResponse = getGTNetPingResponse(myGTNet); 
      break;
    
    case GTNET_FIRST_HANDSHAKE_S:
      // checkHandshake(sourceGTNet, me);
      break;
    case GTNET_UPDATE_SERVERLIST_C:
      break;
      
    default:
      break;
      
    }
    
    
    return meResponse;
  }
  
  private MessageEnvelope getGTNetPingResponse(GTNet myGTnet) {
    GTNetMessage msgRespone = new GTNetMessage(null, new Date(), SendReceivedType.ANSWER.getValue(), null,
        GTNetMessageCodeType.GTNET_PING.getValue(), null, null);
    MessageEnvelope meResponse = new MessageEnvelope(myGTnet.getDomainRemoteName(), msgRespone);
    return meResponse;
  }
  
  private MessageEnvelope checkHandshake(GTNet myGTNet, MessageEnvelope meRequest) throws JsonProcessingException, IllegalArgumentException {
    GTNetMsgRequest gtNetMsgRequest = GTNetModelHelper.getMsgClassByMessageCode(GTNetMessageCodeType.GTNET_FIRST_HANDSHAKE_S);
    GTNet gtNetRemote = objectMapper.treeToValue(meRequest.payload, GTNet.class);
    
    
    addGTNetRemoteWhenNotExists(gtNetRemote, null);
    
    new GTNetMessage(null, new Date(), SendReceivedType.SEND.getValue(), null,
        GTNetMessageCodeType.GTNET_PING.getValue(), null, null);
    
    addGTNetRemoteWhenNotExists(gtNetRemote, null);
    
    return null;
  }
  
  private void addGTNetRemoteWhenNotExists(GTNet gtNetRemote, String remoteToken) {
    GTNet gtNetRemoteExisting = gtNetJpaRepository.findByDomainRemoteName(gtNetRemote.getDomainRemoteName());
    if(gtNetRemoteExisting == null) {
      gtNetRemoteExisting = gtNetRemote;
    }
    gtNetRemoteExisting.setTokenRemote(remoteToken);
    gtNetJpaRepository.save(gtNetRemoteExisting);
  }
  
  
}
