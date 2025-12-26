package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;

public class GTNetMessageAnswerJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessageAnswer>
    implements GTNetMessageAnswerJpaRepositoryCustom {

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Override
  public GTNetMessage getMessageAnswerBy(GTNet myGTNet, GTNet remoteGTNet, MessageEnvelope meRequest) {
    GTNetMessage gtNetMessageAnw = new GTNetMessage();
    List<GTNetMessageAnswer> messageAnswers = gtNetMessageAnswerJpaRepository
        .findByRequestMsgCodeOrderByPriority(meRequest.messageCode);
    if (messageAnswers.isEmpty()) {
      // No auto-response rules configured for this message type
      // Message will require manual admin review
    }

    return gtNetMessageAnw;
  }

  @Override
  public GTNetMessageAnswer saveOnlyAttributes(GTNetMessageAnswer newEntity, final GTNetMessageAnswer existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    // TODO
    return null;
  }

}
