package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.m2m.model.MessageResponse;

public class GTNetMessageJpaRepositoryImpl implements GTNetMessageJpaRepositoryCustom {

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;
  
  @Override
  public GTNetMessage saveMsg(GTNetMessage gtNetMessage) {
    return gtNetMessageJpaRepository.save(gtNetMessage);
  }

  @Override
  public MessageResponse getMsgResponse(MessageEnvelope messageEnvelope) {
    // TODO Auto-generated method stub
    return null;
  }

}
