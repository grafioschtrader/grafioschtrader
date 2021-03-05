package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.MailInbox;

public interface MailInboxJpaRepositoryCustom {

  List<MailInbox> getMailInboxByUser();

  void deleteByIdMailInOut(Integer idMailInOut);

  MailInbox markForRead(Integer idMailInOut);
}
