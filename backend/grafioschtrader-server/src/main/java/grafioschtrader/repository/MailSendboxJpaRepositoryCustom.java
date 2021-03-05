package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.MailSendbox;

public interface MailSendboxJpaRepositoryCustom extends BaseRepositoryCustom<MailSendbox> {

  List<MailSendbox> getMailSendboxByUser();

  MailSendbox sendInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject, String message);

  void deleteByIdMailInOut(Integer idMailInOut);
}
