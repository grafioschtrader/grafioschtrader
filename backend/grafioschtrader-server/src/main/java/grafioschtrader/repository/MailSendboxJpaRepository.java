package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.MailSendbox;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface MailSendboxJpaRepository extends JpaRepository<MailSendbox, Integer>, MailSendboxJpaRepositoryCustom,
    UpdateCreateJpaRepository<MailSendbox> {

  List<MailSendbox> findByIdUserFromOrderBySendTimeAsc(Integer idUserFrom);

  @Modifying
  @Transactional
  @Query(value = "DELETE FROM mail_in_out WHERE id_mail_inout=?1 AND id_user_from=?2", nativeQuery = true)
  int deleteByIdMailInOutAndIdUserTo(Integer idMailInOut, Integer idUser);

}
