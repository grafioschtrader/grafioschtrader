package grafioschtrader.repository;

public interface MailSendRecvReadDelJpaRepositoryCustom  {

  void markForRead(Integer idMailSendRecv, Integer idUser);
  
  void markRoleSingleForDelete(Integer idMailSendRecv, Integer idUser);
}
