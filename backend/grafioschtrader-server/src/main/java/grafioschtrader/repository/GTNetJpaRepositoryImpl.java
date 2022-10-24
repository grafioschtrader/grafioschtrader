package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetHelper;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.m2m.model.IMsgDetails;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.rest.RequestMappings;
import reactor.core.publisher.Mono;

public class GTNetJpaRepositoryImpl implements GTNetJpaRepositoryCustom {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  @Transactional
  public GTNetWithMessages getAllGTNetsWithMessages() {
    return new GTNetWithMessages(gtNetJpaRepository.findAll(), gtNetMessageJpaRepository
        .findAllByOrderByIdGtNetAscTimestampAsc().collect(Collectors.groupingBy(GTNetMessage::getIdGtNet)));
  }

  @Override
  public GTNetWithMessages submitMsg(MsgRequest msgRequest) {
    Class<?> dynamicMsgClass = GTNetHelper.getMsgClassByMessageCode(msgRequest.messageCode);
    try {
      IMsgDetails msg = (IMsgDetails) objectMapper.treeToValue(msgRequest.msgDetails, dynamicMsgClass);
      List<GTNet> gtNetList = gtNetJpaRepository.findAllById(msgRequest.idGTNetTargetDomains);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  private void sendAndSaveMsg(List<GTNet> gtNetList, MsgRequest msgRequest, IMsgDetails msgDetails) {

    for (GTNet gtNet : gtNetList) {
      sendToMachine(gtNet, msgRequest, msgDetails);
      gtNetMessageJpaRepository.saveMsg(new GTNetMessage(gtNet.getIdGtNet(), new Date(),
          SendReceivedType.SEND.getValue(), msgRequest.replyTo, msgRequest.messageCode.getValue(), msgRequest.message),
          msgDetails);
    }

  }

  private void sendToMachine(GTNet gtNet, MsgRequest msgRequest, IMsgDetails msgDetails) {
    MessageEnvelope messageEnvelope = null;
    
    messageEnvelope = WebClient.create(gtNet.getDomainRemoteName()).post()
        .uri(uriBuilder -> uriBuilder.path(RequestMappings.GTNET_M2M_MAP).build())
        .body(Mono.just(messageEnvelope), MessageEnvelope.class)
        .retrieve()
        .bodyToMono(MessageEnvelope.class).block();
  }

}
