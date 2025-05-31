package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;

public class GTNetMessageAnswerJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessageAnswer>
    implements GTNetMessageAnswerJpaRepositoryCustom {

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Override
  public GTNetMessage getMessageAnswerBy(GTNet myGTNet, GTNet remoteGTNet, MessageEnvelope meRequest) {
    GTNetMessage gtNetMessageAnw = new GTNetMessage();
    Optional<GTNetMessageAnswer> messageAnswerOpt = gtNetMessageAnswerJpaRepository.findById(meRequest.messageCode);
    if (messageAnswerOpt.isEmpty()) {
      if (meRequest.messageCode == GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S.getValue()) {

      }
    }

    return gtNetMessageAnw;
  }
}
