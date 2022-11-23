package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetMessage;

public class GTNetMessageJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessage>
    implements GTNetMessageJpaRepositoryCustom {

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Override
  public GTNetMessage saveMsg(GTNetMessage gtNetMessage) {
    return gtNetMessageJpaRepository.save(gtNetMessage);
  }

  @Override
  public GTNetMessage saveOnlyAttributes(GTNetMessage entity, GTNetMessage existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    return gtNetMessageJpaRepository.saveMsg(entity);
  }

}
