package grafioschtrader.repository;

import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;

public interface GTNetJpaRepositoryCustom {
 
  GTNetWithMessages getAllGTNetsWithMessages();
  
  GTNetWithMessages submitMsg(MsgRequest msgRequest);
}
