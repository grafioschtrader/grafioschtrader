package grafiosch.repository;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessageAnswer;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

public interface GTNetMessageAnswerJpaRepositoryCustom extends BaseRepositoryCustom<GTNetMessageAnswer>{
  GTNetMessage getMessageAnswerBy(GTNet myGTNet, GTNet remoteGTNet, MessageEnvelope meRequest);

  
}
