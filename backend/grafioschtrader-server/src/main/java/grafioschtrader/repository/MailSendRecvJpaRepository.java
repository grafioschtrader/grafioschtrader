package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.dto.MailSendRecvDTO;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface MailSendRecvJpaRepository
    extends JpaRepository<MailSendRecv, Integer>, MailSendRecvJpaRepositoryCustom,
    UpdateCreateJpaRepository<MailSendRecv> {

  MailSendRecv findFirstByIdReplyToLocalOrderByIdMailSendRecv(Integer idReplyToLocal);
  
   
  @Query(nativeQuery = true)
  void deleteByIdReplyToLocalAndIdUser(Integer idReplyToLocal, Integer idUser);
  
  @Query(nativeQuery = true)
  void deleteByIdMailSendRecvAndIdUser(Integer idMailSendRecv, Integer idUser);
  
  @Query(nativeQuery = true)
  List<MailSendRecvDTO> findByUserOrGroup(Integer idUser);
  
  @Query(nativeQuery = true)
  List<CountRoleSend> countRoleSend(Integer idUser);
  
  public static interface CountRoleSend {
    Integer getIdReplyToLocal();
    int getNumberOfAnswer();
  }
}
