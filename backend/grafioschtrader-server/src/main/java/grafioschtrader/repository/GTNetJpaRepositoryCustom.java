package grafioschtrader.repository;

import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.gtnet.model.msg.ApplicationInfo;

public interface GTNetJpaRepositoryCustom {
 
  GTNetWithMessages getAllGTNetsWithMessages();
  
  GTNetWithMessages submitMsg(MsgRequest msgRequest);
  
  ApplicationInfo checkRemoteDomainWithActuatorInfo(String remoteDomainName);
}
