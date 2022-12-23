package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.MailInbox;

public interface MailInboxJpaRepository extends JpaRepository<MailInbox, Integer>, MailInboxJpaRepositoryCustom {

  @Query(nativeQuery = true)
  List<MailInbox> findByUserOrGroup(Integer idUserTo);

  MailInbox findByIdMailInOutAndIdUserTo(Integer idMailInOut, Integer idUserTo);

  @Modifying
  @Transactional
  @Query(value = "DELETE FROM mail_in_out WHERE id_mail_inout=?1 AND id_user_to=?2", nativeQuery = true)
  int deleteByIdMailInOutAndIdUserTo(Integer idMailInOut, Integer idUser);
}
