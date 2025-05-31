package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.GTNetMessage;

public interface GTNetMessageJpaRepositoryCustom extends BaseRepositoryCustom<GTNetMessage> {

  GTNetMessage saveMsg(GTNetMessage gtNetMessage);

}
