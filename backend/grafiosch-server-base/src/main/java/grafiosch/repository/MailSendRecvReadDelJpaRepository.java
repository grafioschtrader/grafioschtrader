package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.MailSendRecvReadDel;
import grafiosch.entities.MailSendRecvReadDel.MailSendRecvReadDelKey;

public interface MailSendRecvReadDelJpaRepository
    extends JpaRepository<MailSendRecvReadDel, MailSendRecvReadDelKey>, MailSendRecvReadDelJpaRepositoryCustom {

  @Query(nativeQuery = true)
  void markforDelGroup(Integer idUser, Integer idReplyToLocal);
}
