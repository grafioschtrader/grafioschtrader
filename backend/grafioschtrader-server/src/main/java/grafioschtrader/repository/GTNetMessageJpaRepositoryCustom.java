package grafioschtrader.repository;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.m2m.model.IMsgDetails;

public interface GTNetMessageJpaRepositoryCustom {
  GTNetMessage saveMsg(GTNetMessage gtNetMessage, IMsgDetails msgDetails);
}
