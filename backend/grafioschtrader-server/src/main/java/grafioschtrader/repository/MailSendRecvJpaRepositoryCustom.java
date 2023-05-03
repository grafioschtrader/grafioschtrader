package grafioschtrader.repository;

import grafioschtrader.dto.MailInboxWithSend;
import grafioschtrader.entities.MailSendRecv;

public interface MailSendRecvJpaRepositoryCustom extends BaseRepositoryCustom<MailSendRecv> {
    
  MailInboxWithSend getMailsByUserOrRole();
  
  MailSendRecv markForRead(Integer idMailSendRecv);
  
  void hideDeleteResource(Integer idMailSendRecv);
}
