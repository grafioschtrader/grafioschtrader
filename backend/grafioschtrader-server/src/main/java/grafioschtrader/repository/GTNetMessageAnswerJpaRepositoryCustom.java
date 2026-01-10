package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;

public interface GTNetMessageAnswerJpaRepositoryCustom extends BaseRepositoryCustom<GTNetMessageAnswer>{
  GTNetMessage getMessageAnswerBy(GTNet myGTNet, GTNet remoteGTNet, MessageEnvelope meRequest);

  
}
