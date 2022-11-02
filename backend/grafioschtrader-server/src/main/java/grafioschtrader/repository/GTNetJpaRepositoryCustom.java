package grafioschtrader.repository;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.gtnet.model.msg.ApplicationInfo;

public interface GTNetJpaRepositoryCustom extends BaseRepositoryCustom<GTNet> {
 
  GTNetWithMessages getAllGTNetsWithMessages();
  
  GTNetWithMessages submitMsg(MsgRequest msgRequest);
  
}
