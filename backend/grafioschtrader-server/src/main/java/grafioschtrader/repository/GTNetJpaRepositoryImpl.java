package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetHelper;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.gtnet.model.msg.ApplicationInfo;
import grafioschtrader.m2m.client.BaseDataClient;

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
  public GTNetWithMessages submitMsg(MsgRequest msgRequest) {
    Class<?> dynamicMsgClass = GTNetHelper.getMsgClassByMessageCode(msgRequest.messageCode);
    Object model = objectMapper.convertValue(msgRequest.gtNetMessageParamMap, dynamicMsgClass);
    // TODO check model integrity
    globalparametersJpaRepository.getGTNetMyEntryID();
    List<GTNet> gtNetList = gtNetJpaRepository.findAllById(msgRequest.idGTNetTargetDomains);
    sendAndSaveMsg(gtNetJpaRepository.getReferenceById(getGTNetMyEntryIDOrThrow()).getDomainRemoteName(), gtNetList, msgRequest);
    return this.getAllGTNetsWithMessages();
  }

  /**
   * Message is created and send to the remote servers.
   * 
   * @param gtNetList
   * @param msgRequest
   */
  private void sendAndSaveMsg(String sourceDomain, List<GTNet> gtNetList, MsgRequest msgRequest) {

    for (GTNet gtNetTarget : gtNetList) {
      GTNetMessage gtNetMessage = gtNetMessageJpaRepository.saveMsg(
          new GTNetMessage(gtNetTarget.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), msgRequest.replyTo,
              msgRequest.messageCode.getValue(), msgRequest.message, msgRequest.gtNetMessageParamMap));
      MessageEnvelope me = new MessageEnvelope(sourceDomain, gtNetMessage);
      baseDataClient.sendToMsg(gtNetTarget.getDomainRemoteName() ,me);
    }
  }


  private Integer getGTNetMyEntryIDOrThrow() {
    Integer myIdGtNet = globalparametersJpaRepository.getGTNetMyEntryID();
    if(myIdGtNet == null) {
      throw new IllegalArgumentException("Your machine does not have an entry!");
    }
    return globalparametersJpaRepository.getGTNetMyEntryID();
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

}
