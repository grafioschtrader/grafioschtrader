package grafiosch.repository;

import grafiosch.dto.MailInboxWithSend;
import grafiosch.entities.MailSendRecv;

public interface MailSendRecvJpaRepositoryCustom extends BaseRepositoryCustom<MailSendRecv> {

  MailInboxWithSend getMailsByUserOrRole();

  MailSendRecv markForRead(Integer idMailSendRecv);

  void hideDeleteResource(Integer idMailSendRecv);
}
