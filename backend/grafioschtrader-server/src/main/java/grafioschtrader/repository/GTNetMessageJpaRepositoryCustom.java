package grafioschtrader.repository;

import grafioschtrader.entities.GTNetMessage;

public interface GTNetMessageJpaRepositoryCustom extends BaseRepositoryCustom<GTNetMessage> {
  
  GTNetMessage saveMsg(GTNetMessage gtNetMessage);
  
  
}
