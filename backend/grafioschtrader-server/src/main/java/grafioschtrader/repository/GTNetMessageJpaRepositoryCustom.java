package grafioschtrader.repository;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.m2m.model.MessageResponse;

public interface GTNetMessageJpaRepositoryCustom {
  
  GTNetMessage saveMsg(GTNetMessage gtNetMessage);
  
  MessageResponse getMsgResponse(MessageEnvelope messageEnvelope);
}
