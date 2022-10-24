package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.m2m.model.IMsgDetails;

public class GTNetMessageJpaRepositoryImpl implements GTNetMessageJpaRepositoryCustom {

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;
  
  @Override
  public GTNetMessage saveMsg(GTNetMessage gtNetMessage, IMsgDetails msgDetails) {
    gtNetMessage.setMessageCodeValue(msgDetails);
    return gtNetMessageJpaRepository.save(gtNetMessage);
  }

}
