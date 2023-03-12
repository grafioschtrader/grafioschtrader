package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.MailSendRecvReadDel;
import grafioschtrader.entities.MailSendRecvReadDel.MailSendRecvReadDelKey;

public interface MailSendRecvReadDelJpaRepository
    extends JpaRepository<MailSendRecvReadDel, MailSendRecvReadDelKey>, MailSendRecvReadDelJpaRepositoryCustom {

  @Query(nativeQuery = true)
  void markforDelGroup(Integer idUser, Integer idReplyToLocal);
}
