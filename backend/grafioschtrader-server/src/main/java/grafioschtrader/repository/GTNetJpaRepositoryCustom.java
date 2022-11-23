package grafioschtrader.repository;

import grafioschtrader.entities.GTNet;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;

public interface GTNetJpaRepositoryCustom extends BaseRepositoryCustom<GTNet> {
 
  GTNetWithMessages getAllGTNetsWithMessages();
  
  GTNetWithMessages submitMsg(MsgRequest msgRequest);
  
  MessageEnvelope getMsgResponse(MessageEnvelope messageEnvelope);
}
