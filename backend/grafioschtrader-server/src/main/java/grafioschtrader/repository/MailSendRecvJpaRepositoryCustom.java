package grafioschtrader.repository;

import grafioschtrader.dto.MailInboxWithSend;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.types.ReplyToRolePrivateType;

public interface MailSendRecvJpaRepositoryCustom extends BaseRepositoryCustom<MailSendRecv> {

  Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String subject,
      String message);
  
  Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject,
      String message, Integer idReplyToLocal, ReplyToRolePrivateType replyToRolePrivate);
  
  MailInboxWithSend getMailsByUserOrRole();

  MailSendRecv markForRead(Integer idMailSendRecv);
  
  void hideDeleteResource(Integer idMailSendRecv);
}
